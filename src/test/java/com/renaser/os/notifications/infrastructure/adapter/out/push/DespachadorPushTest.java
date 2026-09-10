package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.ResultadoEnvioPush;
import com.renaser.os.notifications.application.ports.out.push.ResultadoEnvioPush.Estado;
import com.renaser.os.notifications.application.ports.out.push.TransportePush;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El reparto de un push a su canal.
 *
 * <p>Lo que se prueba es que un token sin transporte deje de desaparecer en silencio. Antes el
 * adaptador web filtraba por {@code plataforma == WEB} y descartaba los de iOS y Android sin
 * decir nada: la app quedaba creyendo que el aviso salió cuando nunca hubo a dónde mandarlo.
 */
class DespachadorPushTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final UserId USUARIO = UserId.of(UUID.randomUUID());

    private static TokenPush token(PlataformaPush plataforma) {
        return TokenPush.rehydrate(TokenPushId.of(UUID.randomUUID()), USUARIO, "tok-" + plataforma,
                plataforma, AHORA, AHORA);
    }

    /** Transporte de prueba: registra lo que recibió y devuelve lo que se le pida. */
    private static final class TransporteDoble implements TransportePush {

        private final List<PlataformaPush> atendidas;
        private final Estado respuesta;
        final List<String> rutasRecibidas = new ArrayList<>();

        TransporteDoble(List<PlataformaPush> atendidas, Estado respuesta) {
            this.atendidas = atendidas;
            this.respuesta = respuesta;
        }

        @Override
        public boolean atiende(PlataformaPush plataforma) {
            return atendidas.contains(plataforma);
        }

        @Override
        public String nombre() {
            return "doble-" + atendidas;
        }

        @Override
        public ResultadoEnvioPush entregar(TokenPush token, String titulo, String cuerpo, String rutaApp) {
            rutasRecibidas.add(rutaApp);
            return new ResultadoEnvioPush(token.id(), respuesta, null);
        }
    }

    @Test
    @DisplayName("cada token va al transporte de SU plataforma")
    void repartePorPlataforma() {
        TransporteDoble web = new TransporteDoble(List.of(PlataformaPush.WEB), Estado.ENTREGADO);
        TransporteDoble nativo = new TransporteDoble(
                List.of(PlataformaPush.IOS, PlataformaPush.ANDROID), Estado.ENTREGADO);
        DespachadorPush despachador = new DespachadorPush(List.of(web, nativo));

        List<ResultadoEnvioPush> resultados = despachador.enviar(
                List.of(token(PlataformaPush.WEB), token(PlataformaPush.IOS), token(PlataformaPush.ANDROID)),
                "Hay novedades", "de acompanamiento", "/mentor/groups/x/learners/y");

        assertThat(resultados).hasSize(3);
        assertThat(resultados).allSatisfy(r -> assertThat(r.estado()).isEqualTo(Estado.ENTREGADO));
        assertThat(web.rutasRecibidas).hasSize(1);
        assertThat(nativo.rutasRecibidas).hasSize(2);
    }

    @Test
    @DisplayName("un token sin transporte se reporta, no se descarta en silencio")
    void sinTransporteSeReporta() {
        TransporteDoble soloWeb = new TransporteDoble(List.of(PlataformaPush.WEB), Estado.ENTREGADO);
        DespachadorPush despachador = new DespachadorPush(List.of(soloWeb));

        List<ResultadoEnvioPush> resultados = despachador.enviar(
                List.of(token(PlataformaPush.ANDROID)), "t", "c", null);

        assertThat(resultados).singleElement()
                .satisfies(r -> assertThat(r.estado()).isEqualTo(Estado.SIN_TRANSPORTE));
    }

    @Test
    @DisplayName("la ruta de destino llega al transporte: el push abre donde corresponde")
    void laRutaViaja() {
        TransporteDoble web = new TransporteDoble(List.of(PlataformaPush.WEB), Estado.ENTREGADO);
        DespachadorPush despachador = new DespachadorPush(List.of(web));

        despachador.enviar(List.of(token(PlataformaPush.WEB)), "t", "c", "/mentor/groups/g/learners/a");

        assertThat(web.rutasRecibidas).containsExactly("/mentor/groups/g/learners/a");
    }

    @Test
    @DisplayName("un transporte que lanza no tumba el lote: se traduce a fallo temporal")
    void transporteQueLanzaNoRompeElLote() {
        TransportePush explota = new TransportePush() {
            @Override
            public boolean atiende(PlataformaPush plataforma) {
                return plataforma == PlataformaPush.WEB;
            }

            @Override
            public String nombre() {
                return "explota";
            }

            @Override
            public ResultadoEnvioPush entregar(TokenPush t, String titulo, String cuerpo, String ruta) {
                throw new IllegalStateException("proveedor caido");
            }
        };
        TransporteDoble nativo = new TransporteDoble(List.of(PlataformaPush.ANDROID), Estado.ENTREGADO);
        DespachadorPush despachador = new DespachadorPush(List.of(explota, nativo));

        List<ResultadoEnvioPush> resultados = despachador.enviar(
                List.of(token(PlataformaPush.WEB), token(PlataformaPush.ANDROID)), "t", "c", null);

        assertThat(resultados.get(0).estado()).isEqualTo(Estado.FALLO_TEMPORAL);
        // El segundo se entrega igual: aislar el fallo es el punto.
        assertThat(resultados.get(1).estado()).isEqualTo(Estado.ENTREGADO);
    }

    @Test
    @DisplayName("dos transportes peleando la misma plataforma revientan al ARRANCAR, no en caliente")
    void ambiguedadSeDetectaAlArrancar() {
        TransporteDoble uno = new TransporteDoble(List.of(PlataformaPush.WEB), Estado.ENTREGADO);
        TransporteDoble otro = new TransporteDoble(List.of(PlataformaPush.WEB), Estado.ENTREGADO);

        // Elegir "el primero" haria que el canal efectivo dependiera del orden de los beans.
        assertThatThrownBy(() -> new DespachadorPush(List.of(uno, otro)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEB");
    }

    @Test
    @DisplayName("sin tokens no se llama a ningun transporte")
    void sinTokensNoHaceNada() {
        TransporteDoble web = new TransporteDoble(List.of(PlataformaPush.WEB), Estado.ENTREGADO);

        assertThat(new DespachadorPush(List.of(web)).enviar(List.of(), "t", "c", null)).isEmpty();
        assertThat(web.rutasRecibidas).isEmpty();
    }

    @Test
    @DisplayName("un token invalido se distingue de uno temporal: solo el primero se desactiva")
    void distingueInvalidoDeTemporal() {
        assertThat(ResultadoEnvioPush.invalido(TokenPushId.of(UUID.randomUUID()), "410").reintentable())
                .isFalse();
        assertThat(ResultadoEnvioPush.temporal(TokenPushId.of(UUID.randomUUID()), "503").reintentable())
                .isTrue();
        assertThat(ResultadoEnvioPush.entregado(TokenPushId.of(UUID.randomUUID())).reintentable()).isFalse();
    }

    /** Devuelve la secuencia que se le dé, un estado por intento, y cuenta cuántas veces la llamaron. */
    private static final class TransporteSecuencia implements TransportePush {

        private final List<Estado> secuencia;
        int intentos = 0;

        TransporteSecuencia(Estado... secuencia) {
            this.secuencia = List.of(secuencia);
        }

        @Override
        public boolean atiende(PlataformaPush plataforma) {
            return true;
        }

        @Override
        public String nombre() {
            return "secuencia";
        }

        @Override
        public ResultadoEnvioPush entregar(TokenPush token, String titulo, String cuerpo, String rutaApp) {
            Estado estado = secuencia.get(Math.min(intentos, secuencia.size() - 1));
            intentos++;
            return new ResultadoEnvioPush(token.id(), estado, null);
        }
    }

    /** Anota cuánto se pidió esperar, sin esperar de verdad. */
    private static final class EsperaFingida implements DespachadorPush.Espera {

        final List<Long> esperas = new ArrayList<>();

        @Override
        public void milisegundos(long ms) {
            esperas.add(ms);
        }
    }

    @Test
    @DisplayName("un fallo temporal se reintenta, y si el segundo intento entrega el resultado es ENTREGADO")
    void reintentaLoTemporal() {
        TransporteSecuencia transporte = new TransporteSecuencia(Estado.FALLO_TEMPORAL, Estado.ENTREGADO);
        EsperaFingida espera = new EsperaFingida();
        DespachadorPush despachador = new DespachadorPush(List.of(transporte), espera);

        List<ResultadoEnvioPush> resultados = despachador.enviar(
                List.of(token(PlataformaPush.ANDROID)), "t", "c", null);

        assertThat(resultados).singleElement()
                .satisfies(r -> assertThat(r.estado()).isEqualTo(Estado.ENTREGADO));
        assertThat(transporte.intentos).isEqualTo(2);
        assertThat(espera.esperas).containsExactly(250L);
    }

    @Test
    @DisplayName("la espera crece entre reintentos y se detiene en dos: no reintenta para siempre")
    void laEsperaCreceYEstaAcotada() {
        TransporteSecuencia transporte = new TransporteSecuencia(Estado.FALLO_TEMPORAL);
        EsperaFingida espera = new EsperaFingida();
        DespachadorPush despachador = new DespachadorPush(List.of(transporte), espera);

        List<ResultadoEnvioPush> resultados = despachador.enviar(
                List.of(token(PlataformaPush.IOS)), "t", "c", null);

        // 1 intento + 2 reintentos. Si alguien agrega un tercero sin pensarlo, esto lo dice.
        assertThat(transporte.intentos).isEqualTo(3);
        assertThat(espera.esperas).containsExactly(250L, 750L);
        assertThat(resultados).singleElement()
                .satisfies(r -> assertThat(r.estado()).isEqualTo(Estado.FALLO_TEMPORAL));
    }

    @Test
    @DisplayName("un token invalido NO se reintenta: la app se desinstalo, insistir es tirar trabajo")
    void noReintentaElTokenInvalido() {
        TransporteSecuencia transporte = new TransporteSecuencia(Estado.TOKEN_INVALIDO);
        EsperaFingida espera = new EsperaFingida();
        DespachadorPush despachador = new DespachadorPush(List.of(transporte), espera);

        despachador.enviar(List.of(token(PlataformaPush.ANDROID)), "t", "c", null);

        assertThat(transporte.intentos).isEqualTo(1);
        assertThat(espera.esperas).isEmpty();
    }

    @Test
    @DisplayName("un envio entregado no se repite: dos banners son peor que el fallo")
    void noReintentaLoEntregado() {
        TransporteSecuencia transporte = new TransporteSecuencia(Estado.ENTREGADO);
        EsperaFingida espera = new EsperaFingida();
        DespachadorPush despachador = new DespachadorPush(List.of(transporte), espera);

        despachador.enviar(List.of(token(PlataformaPush.WEB)), "t", "c", null);

        assertThat(transporte.intentos).isEqualTo(1);
        assertThat(espera.esperas).isEmpty();
    }

    @Test
    @DisplayName("el fallo temporal de un token no consume los reintentos del siguiente")
    void cadaTokenTieneSusPropiosReintentos() {
        TransporteSecuencia transporte = new TransporteSecuencia(Estado.FALLO_TEMPORAL);
        EsperaFingida espera = new EsperaFingida();
        DespachadorPush despachador = new DespachadorPush(List.of(transporte), espera);

        despachador.enviar(
                List.of(token(PlataformaPush.ANDROID), token(PlataformaPush.IOS)), "t", "c", null);

        assertThat(transporte.intentos).isEqualTo(6);
        assertThat(espera.esperas).containsExactly(250L, 750L, 250L, 750L);
    }
}

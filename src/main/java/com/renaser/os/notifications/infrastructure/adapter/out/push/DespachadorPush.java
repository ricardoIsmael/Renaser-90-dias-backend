package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.application.ports.out.push.ResultadoEnvioPush;
import com.renaser.os.notifications.application.ports.out.push.TransportePush;
import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reparte cada token al transporte de su plataforma.
 *
 * <p>Es el único {@link PushPort} del sistema. Antes esa responsabilidad vivía dentro del
 * adaptador web, que filtraba por {@code plataforma == WEB} y descartaba en silencio los tokens
 * de iOS y Android: un aprendiz con la app instalada aparecía como "notificado" sin que nada
 * hubiera salido. Ahora un token sin transporte devuelve {@code SIN_TRANSPORTE} y queda visible.
 *
 * <p>Un transporte que falla no afecta a los demás: cada token se intenta por separado y una
 * excepción inesperada se traduce a fallo temporal en vez de cortar el lote.
 */
@Component
class DespachadorPush implements PushPort {

    private static final Logger log = LoggerFactory.getLogger(DespachadorPush.class);

    /**
     * Cuánto se espera antes de cada reintento, en milisegundos. Creciente y CORTA a propósito.
     *
     * <p>Un fallo temporal de Expo es casi siempre un 429 o un pico de latencia, y eso se pasa en
     * cientos de milisegundos. Esperar minutos no aumenta la probabilidad de éxito lo suficiente
     * como para justificar tener un hilo del executor ocupado tanto tiempo: el aviso ya está en la
     * bandeja de la aplicación y esa es la vía que no depende del proveedor.
     *
     * <p>Dos reintentos y no más. El tercero es donde la espera empieza a costar más que lo que
     * recupera; a partir de ahí lo correcto es una reentrega diferida, que necesita persistir el
     * pendiente y todavía no existe (ver el javadoc de {@link #enviar}).
     */
    private static final long[] ESPERAS_MS = { 250L, 750L };

    private final Map<PlataformaPush, TransportePush> porPlataforma = new EnumMap<>(PlataformaPush.class);

    /**
     * Cómo se espera entre reintentos. Se inyecta para que las pruebas no tarden un segundo cada
     * una: con {@code Thread::sleep} de verdad, probar dos reintentos cuesta un segundo de reloj
     * y la suite entera lo paga.
     */
    private final Espera espera;

    /** Pausa entre reintentos. Existe solo para poder sustituirla en las pruebas. */
    interface Espera {
        void milisegundos(long ms) throws InterruptedException;
    }

    /**
     * El mapa se arma UNA vez, al arrancar, y ahí mismo se detecta la ambigüedad. Que dos
     * transportes se peleen la misma plataforma es un error de configuración: resolverlo en
     * caliente eligiendo "el primero" haría que el canal efectivo dependiera del orden en que
     * Spring construyó los beans, y eso cambia sin que nadie lo toque.
     */
    @Autowired
    DespachadorPush(List<TransportePush> transportes) {
        this(transportes, ms -> TimeUnit.MILLISECONDS.sleep(ms));
    }

    /**
     * Solo para pruebas. Va SIN {@code @Autowired} y el otro CON: con dos constructores y ninguno
     * marcado, Spring no elige — busca el vacio, no lo encuentra y no levanta el contexto. Lo
     * aprendi rompiendo 413 pruebas de integracion de una vez: {@code DespachadorPushTest} lo
     * construye a mano, asi que el cableado real no lo ejercitaba ninguna prueba de esta clase.
     */
    DespachadorPush(List<TransportePush> transportes, Espera espera) {
        this.espera = espera;
        for (PlataformaPush plataforma : PlataformaPush.values()) {
            List<TransportePush> candidatos = transportes.stream()
                    .filter(t -> t.atiende(plataforma))
                    .toList();
            if (candidatos.size() > 1) {
                throw new IllegalStateException("Dos transportes atienden " + plataforma + ": "
                        + candidatos.stream().map(TransportePush::nombre).toList());
            }
            if (candidatos.size() == 1) {
                porPlataforma.put(plataforma, candidatos.getFirst());
            }
        }
        log.info("[notifications.DespachadorPush] transportes activos: {}", porPlataforma.keySet());
    }

    /**
     * Reparte cada token y reintenta lo que el proveedor declaró pasajero.
     *
     * <p><b>Solo se reintenta {@code FALLO_TEMPORAL}.</b> Un {@code TOKEN_INVALIDO} es la app
     * desinstalada: repetirlo es tirar trabajo para siempre, y encima retrasa la desactivación del
     * token. {@code SIN_TRANSPORTE} es configuración, no una falla de red — reintentar no la
     * arregla. Y {@code ENTREGADO} reintentado sería un aviso duplicado en el teléfono, que es
     * peor que el fallo original.
     *
     * <p>Duplicar un envío es seguro igual: el aviso ya se dedujo por episodio antes de llegar acá
     * ({@code ReglasDeAviso.claveDeDeduplicacion}) y la fila tiene índice único por
     * {@code origenEventoId}. Lo que se evita al no reintentar los entregados es la molestia de
     * dos banners, no una inconsistencia.
     *
     * <p><b>Lo que esto NO es:</b> una garantía de entrega. Si los dos reintentos fallan, el aviso
     * queda solo en la bandeja de la aplicación y el resultado temporal queda en el log. Cerrar
     * ese hueco pide guardar el pendiente y reentregarlo desde un job, no esperar más acá.
     */
    @Override
    public List<ResultadoEnvioPush> enviar(List<TokenPush> tokens, String titulo, String cuerpo, String rutaApp) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        List<ResultadoEnvioPush> resultados = new ArrayList<>(tokens.size());
        for (TokenPush token : tokens) {
            TransportePush transporte = porPlataforma.get(token.plataforma());
            if (transporte == null) {
                resultados.add(ResultadoEnvioPush.sinTransporte(token.id(), token.plataforma().name()));
                continue;
            }
            resultados.add(conReintentos(transporte, token, titulo, cuerpo, rutaApp));
        }
        return resultados;
    }

    private ResultadoEnvioPush conReintentos(TransportePush transporte, TokenPush token,
                                             String titulo, String cuerpo, String rutaApp) {
        ResultadoEnvioPush resultado = unIntento(transporte, token, titulo, cuerpo, rutaApp);
        for (int i = 0; i < ESPERAS_MS.length && resultado.estado() == ResultadoEnvioPush.Estado.FALLO_TEMPORAL; i++) {
            if (!esperar(ESPERAS_MS[i])) {
                // Interrumpido: el que manda es quien apaga la aplicación. Se devuelve lo último
                // que se sabe en vez de seguir intentando durante el apagado.
                return resultado;
            }
            log.debug("[notifications.DespachadorPush] reintento {} para el token {}", i + 1, token.id());
            resultado = unIntento(transporte, token, titulo, cuerpo, rutaApp);
        }
        return resultado;
    }

    private ResultadoEnvioPush unIntento(TransportePush transporte, TokenPush token,
                                         String titulo, String cuerpo, String rutaApp) {
        try {
            return transporte.entregar(token, titulo, cuerpo, rutaApp);
        } catch (RuntimeException e) {
            // El contrato dice que un transporte no lanza. Si igual lo hace, se trata como
            // fallo temporal: es preferible reintentar de mas que perder el aviso en silencio.
            log.warn("[notifications.DespachadorPush] el transporte {} lanzo: {}",
                    token.plataforma(), e.getMessage());
            return ResultadoEnvioPush.temporal(token.id(), e.getMessage());
        }
    }

    /** {@code false} si el hilo fue interrumpido; la bandera se restaura, no se traga. */
    private boolean esperar(long ms) {
        try {
            espera.milisegundos(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

package com.renaser.os.habits.domain.model.aviso;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La regla completa de CUANDO avisar, sin Spring y sin base.
 *
 * <p>Todos los casos usan {@code America/Lima} y varios usan un reloj entre 00:00 y 05:00 UTC
 * (regla 03): con el padron en UTC-5, esa franja cae en el dia calendario ANTERIOR, y es
 * exactamente la que esconde los bugs de zona de la familia E-91.
 */
class CalculadoraAvisosHabitoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 5);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final CalculadoraAvisosHabito CALCULADORA =
            new CalculadoraAvisosHabito(Duration.ofMinutes(15), Duration.ofMinutes(30));

    /** Habito de 20:00 a 22:00 hora de Lima: su plazo cae de madrugada UTC del dia siguiente. */
    private static VentanaEntrega ventanaNocturnaEnLima() {
        return VentanaEntrega.calcular(FECHA, LocalTime.of(20, 0), LocalTime.of(22, 0), LIMA, null);
    }

    @Test
    @DisplayName("sin ventana (habito sin horario) no se avisa nada")
    void sinVentanaNoHayAvisos() {
        assertThat(CALCULADORA.debidosAhora(null, Instant.parse("2026-09-05T12:00:00Z"))).isEmpty();
    }

    @Test
    @DisplayName("una antelacion negativa es un error de configuracion, no un aviso raro en produccion")
    void rechazaAntelacionNegativa() {
        assertThatThrownBy(() -> new CalculadoraAvisosHabito(Duration.ofMinutes(-1), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dentro de los 15 min previos al inicio -> aviso de INICIO con los minutos que faltan")
    void avisaElInicioDentroDeLaFranja() {
        // 20:00 Lima = 01:00 UTC del 6. Diez minutos antes: 00:50 UTC del 6 (dia local anterior).
        List<AvisoHabito> avisos = CALCULADORA.debidosAhora(ventanaNocturnaEnLima(),
                Instant.parse("2026-09-06T00:50:00Z"));

        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).tipo()).isEqualTo(TipoAvisoHabito.INICIO);
        assertThat(avisos.get(0).minutosQueFaltan()).isEqualTo(10);
    }

    @Test
    @DisplayName("mas de 15 min antes del inicio todavia no se avisa")
    void noAvisaDemasiadoTemprano() {
        // 00:40 UTC del 6 = 19:40 Lima: faltan 20 minutos, mas que la antelacion de 15.
        assertThat(CALCULADORA.debidosAhora(ventanaNocturnaEnLima(), Instant.parse("2026-09-06T00:40:00Z"))).isEmpty();
    }

    @Test
    @DisplayName("pasada la hora de inicio ya no se avisa que empieza: seria mentira")
    void noAvisaElInicioUnaVezQueEmpezo() {
        // 01:05 UTC del 6 = 20:05 Lima, cinco minutos DESPUES de la hora de disparo.
        List<AvisoHabito> avisos = CALCULADORA.debidosAhora(ventanaNocturnaEnLima(),
                Instant.parse("2026-09-06T01:05:00Z"));

        assertThat(avisos).noneMatch(aviso -> aviso.tipo() == TipoAvisoHabito.INICIO);
    }

    @Test
    @DisplayName("dentro de los 30 min previos al plazo -> aviso de POR_VENCER")
    void avisaElVencimientoDentroDeLaFranja() {
        VentanaEntrega ventana = ventanaNocturnaEnLima();
        Instant veinteMinutosAntes = ventana.plazoEvidencia().minus(Duration.ofMinutes(20));

        List<AvisoHabito> avisos = CALCULADORA.debidosAhora(ventana, veinteMinutosAntes);

        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).tipo()).isEqualTo(TipoAvisoHabito.POR_VENCER);
        assertThat(avisos.get(0).minutosQueFaltan()).isEqualTo(20);
        assertThat(avisos.get(0).momento()).isEqualTo(ventana.plazoEvidencia());
    }

    @Test
    @DisplayName("se avisa contra el PLAZO, no contra la hora limite: dentro de la extension no se pierde nada")
    void noAvisaEnLaHoraLimiteSinoEnElPlazo() {
        VentanaEntrega ventana = ventanaNocturnaEnLima();
        // Justo en el ancla (la hora limite, 22:00 Lima): la extension recien empieza y sigue
        // pagando 10 puntos, asi que no hay nada que avisar todavia.
        assertThat(CALCULADORA.debidosAhora(ventana, ventana.instanteAncla())).isEmpty();
    }

    @Test
    @DisplayName("pasado el plazo no se avisa nada mas")
    void noAvisaDespuesDelPlazo() {
        VentanaEntrega ventana = ventanaNocturnaEnLima();
        assertThat(CALCULADORA.debidosAhora(ventana, ventana.plazoEvidencia().plus(Duration.ofSeconds(1)))).isEmpty();
    }

    @Test
    @DisplayName("antelacion en cero apaga ese aviso, sin ninguna bandera aparte")
    void antelacionCeroApagaElAviso() {
        CalculadoraAvisosHabito soloVencimiento =
                new CalculadoraAvisosHabito(Duration.ZERO, Duration.ofMinutes(30));

        List<AvisoHabito> avisos = soloVencimiento.debidosAhora(ventanaNocturnaEnLima(),
                Instant.parse("2026-09-06T00:50:00Z"));

        assertThat(avisos).isEmpty();
    }

    @Test
    @DisplayName("un habito sin hora de disparo (solo cierre) no tiene inicio que avisar")
    void sinHoraDeDisparoNoHayAvisoDeInicio() {
        VentanaEntrega soloCierre = VentanaEntrega.calcular(FECHA, null, LocalTime.of(22, 0), LIMA, null);
        Instant veinteMinutosAntesDelPlazo = soloCierre.plazoEvidencia().minus(Duration.ofMinutes(20));

        List<AvisoHabito> avisos = CALCULADORA.debidosAhora(soloCierre, veinteMinutosAntesDelPlazo);

        assertThat(avisos).extracting(AvisoHabito::tipo).containsExactly(TipoAvisoHabito.POR_VENCER);
    }

    @Test
    @DisplayName("los dos avisos tienen claves de deduplicacion distintas para el mismo registro")
    void losDosAvisosNoSePisanEnLaDeduplicacion() {
        RegistroHabitoId registroId = RegistroHabitoId.of(UUID.randomUUID());

        assertThat(TipoAvisoHabito.INICIO.claveIdempotencia(registroId))
                .isNotEqualTo(TipoAvisoHabito.POR_VENCER.claveIdempotencia(registroId));
    }

    @Test
    @DisplayName("la clave de deduplicacion es deterministica: dos calculos del mismo aviso dan lo mismo")
    void laClaveEsDeterministica() {
        RegistroHabitoId registroId = RegistroHabitoId.of(UUID.randomUUID());

        assertThat(TipoAvisoHabito.INICIO.claveIdempotencia(registroId))
                .isEqualTo(TipoAvisoHabito.INICIO.claveIdempotencia(registroId));
    }

    @Test
    @DisplayName("faltando menos de un minuto se dice 1 minuto, nunca 0")
    void redondeaLosMinutosHaciaArriba() {
        VentanaEntrega ventana = ventanaNocturnaEnLima();
        Instant treintaSegundosAntes = ventana.plazoEvidencia().minus(Duration.ofSeconds(30));

        List<AvisoHabito> avisos = CALCULADORA.debidosAhora(ventana, treintaSegundosAntes);

        assertThat(avisos.get(0).minutosQueFaltan()).isEqualTo(1);
    }
}

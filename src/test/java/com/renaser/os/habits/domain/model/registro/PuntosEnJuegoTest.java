package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los puntos en juego son la MISMA escala de D-97 vista desde antes de completar. Estos casos
 * son deliberadamente los espejos de {@link ResultadoOtorgamientoTest}: si algun dia los dos
 * dejan de coincidir, es que alguien duplico la regla en vez de reusarla.
 */
class PuntosEnJuegoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 5);
    /** La zona real del padron. Nada de UTC en un test de horarios: es lo que tapaba E-91. */
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static VentanaEntrega ventanaDe(int horaInicio, int horaLimite) {
        return VentanaEntrega.calcular(FECHA, LocalTime.of(horaInicio, 0), LocalTime.of(horaLimite, 0), LIMA, null);
    }

    @Test
    @DisplayName("sin ventana (habito sin ninguna hora) -> puntaje completo y sin plazo")
    void sinVentanaPagaCompletoYNoVence() {
        PuntosEnJuego enJuego = PuntosEnJuego.de(null, Instant.parse("2026-09-05T23:00:00Z"));

        assertThat(enJuego.siCompletaAhora()).isEqualTo(ResultadoOtorgamiento.PUNTOS_COMPLETOS);
        assertThat(enJuego.maximo()).isEqualTo(ResultadoOtorgamiento.PUNTOS_COMPLETOS);
        assertThat(enJuego.plazo()).isNull();
        assertThat(enJuego.vencido(Instant.parse("2030-01-01T00:00:00Z"))).isFalse();
        assertThat(enJuego.restante(Instant.parse("2026-09-05T23:00:00Z"))).isNull();
    }

    @Test
    @DisplayName("antes del ancla -> 10 puntos")
    void antesDelAnclaPagaCompleto() {
        VentanaEntrega ventana = ventanaDe(6, 8);

        // 12:00 UTC = 07:00 en Lima, una hora antes del cierre.
        PuntosEnJuego enJuego = PuntosEnJuego.de(ventana, Instant.parse("2026-09-05T12:00:00Z"));

        assertThat(enJuego.siCompletaAhora()).isEqualTo(10);
        assertThat(enJuego.vencido(Instant.parse("2026-09-05T12:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("dentro de la extension de 3 h -> sigue pagando 10 (D-97)")
    void dentroDeLaExtensionSiguePagandoCompleto() {
        VentanaEntrega ventana = ventanaDe(6, 8);

        // 13:00 UTC = 08:00 Lima es el ancla; 15:00 UTC = 10:00 Lima, dos horas despues.
        assertThat(PuntosEnJuego.de(ventana, Instant.parse("2026-09-05T15:00:00Z")).siCompletaAhora()).isEqualTo(10);
    }

    @Test
    @DisplayName("en los 10 minutos de gracia decae de 10 a 5, dos minutos por punto")
    void enGraciaDecae() {
        VentanaEntrega ventana = ventanaDe(6, 8);
        Instant finDeExtension = ventana.instanteAncla().plus(ventana.extension());

        assertThat(PuntosEnJuego.de(ventana, finDeExtension.plus(Duration.ofMinutes(4))).siCompletaAhora())
                .isEqualTo(8);
        assertThat(PuntosEnJuego.de(ventana, finDeExtension.plus(Duration.ofMinutes(10))).siCompletaAhora())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("pasado el plazo -> 0 puntos y vencido")
    void pasadoElPlazoNoHayNadaEnJuego() {
        VentanaEntrega ventana = ventanaDe(6, 8);
        Instant tarde = ventana.plazoEvidencia().plus(Duration.ofSeconds(1));

        PuntosEnJuego enJuego = PuntosEnJuego.de(ventana, tarde);

        assertThat(enJuego.siCompletaAhora()).isZero();
        assertThat(enJuego.vencido(tarde)).isTrue();
        assertThat(enJuego.restante(tarde)).isZero();
    }

    @Test
    @DisplayName("el plazo expuesto es el mismo de la ventana, y restante() nunca es negativo")
    void exponeElPlazoDeLaVentana() {
        VentanaEntrega ventana = ventanaDe(6, 8);
        Instant unaHoraAntes = ventana.plazoEvidencia().minus(Duration.ofHours(1));

        PuntosEnJuego enJuego = PuntosEnJuego.de(ventana, unaHoraAntes);

        assertThat(enJuego.plazo()).isEqualTo(ventana.plazoEvidencia());
        assertThat(enJuego.restante(unaHoraAntes)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("reloj entre 00:00 y 05:00 UTC: el habito de la vispera en Lima sigue vivo y paga")
    void conRelojDeMadrugadaUtcElHabitoDeLaVisperaEnLimaSigueVivo() {
        // Regla 03: todo test de comportamiento diario debe incluir una hora UTC que en Lima caiga
        // en el dia ANTERIOR. 02:00 UTC del 6 = 21:00 del 5 en Lima.
        VentanaEntrega ventana = ventanaDe(20, 22);
        Instant madrugadaUtc = Instant.parse("2026-09-06T02:00:00Z");

        PuntosEnJuego enJuego = PuntosEnJuego.de(ventana, madrugadaUtc);

        // 22:00 en Lima es 03:00 UTC del dia siguiente: a las 02:00 UTC todavia falta una hora.
        assertThat(enJuego.siCompletaAhora()).isEqualTo(10);
        assertThat(enJuego.vencido(madrugadaUtc)).isFalse();
        assertThat(enJuego.restante(madrugadaUtc)).isGreaterThan(Duration.ofHours(1));
    }
}

package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** habitWindowFrom/limitInstantFor/effectiveExtensionMs traducidos 1:1 (service.ts:280-420). */
class VentanaEntregaTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    @DisplayName("sin ninguna hora configurada -> null (no hay ventana)")
    void sinHorarioNoHayVentana() {
        assertThat(VentanaEntrega.calcular(FECHA, null, null, UTC, null)).isNull();
    }

    @Test
    @DisplayName("solo hora de inicio (sin hora fin) -> el ancla es la hora de inicio, mismo fallback que el repo viejo")
    void soloHoraInicioUsaComoAncla() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(7, 0), null, UTC, null);
        assertThat(v.tieneHoraLimite()).isFalse();
        assertThat(v.instanteAncla()).isEqualTo(FECHA.atTime(7, 0).atZone(UTC).toInstant());
    }

    @Test
    @DisplayName("hora fin presente -> el ancla es la hora fin, no la de inicio")
    void horaFinEsElAncla() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(6, 0), LocalTime.of(8, 0), UTC, null);
        assertThat(v.tieneHoraLimite()).isTrue();
        assertThat(v.instanteAncla()).isEqualTo(FECHA.atTime(8, 0).atZone(UTC).toInstant());
    }

    @Test
    @DisplayName("ventana que cruza medianoche (22:00 -> 02:00): la hora fin cae al dia siguiente")
    void ventanaCruzaMedianoche() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(22, 0), LocalTime.of(2, 0), UTC, null);
        Instant esperado = FECHA.plusDays(1).atTime(2, 0).atZone(UTC).toInstant();
        assertThat(v.instanteAncla()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("extension default de 3h se respeta cuando hay margen de sobra hasta medianoche")
    void extensionDefaultSinRecorte() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(6, 0), LocalTime.of(8, 0), UTC, null);
        assertThat(v.extension()).isEqualTo(Duration.ofHours(3));
    }

    @Test
    @DisplayName("extension propia del habito (horas_extra_evidencia) reemplaza el default")
    void extensionPropiaDelHabito() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(6, 0), LocalTime.of(8, 0), UTC, 1);
        assertThat(v.extension()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("la extension se recorta contra la medianoche siguiente, nunca promete mas de lo que el cron respeta")
    void extensionSeRecortaContraMedianoche() {
        // ancla 23:30, gracia 10min -> 23:40; hasta medianoche quedan 20 min, no 3h
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(20, 0), LocalTime.of(23, 30), UTC, null);
        assertThat(v.extension()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    @DisplayName("si la gracia sola ya pasa la medianoche, la extension queda en 0 (nunca negativa)")
    void extensionNuncaNegativa() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(20, 0), LocalTime.of(23, 55), UTC, null);
        assertThat(v.extension()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("plazoEvidencia = ancla + 10 min de gracia + extension")
    void plazoEvidenciaSumaGraciaYExtension() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(6, 0), LocalTime.of(8, 0), UTC, 2);
        Instant esperado = FECHA.atTime(8, 0).atZone(UTC).toInstant()
                .plus(Duration.ofMinutes(10)).plus(Duration.ofHours(2));
        assertThat(v.plazoEvidencia()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("vencida() es true justo despues del plazo, false justo antes o en el borde")
    void vencidaEnElBorde() {
        VentanaEntrega v = VentanaEntrega.calcular(FECHA, LocalTime.of(6, 0), LocalTime.of(8, 0), UTC, 0);
        assertThat(v.vencida(v.plazoEvidencia())).isFalse();
        assertThat(v.vencida(v.plazoEvidencia().plusSeconds(1))).isTrue();
    }
}

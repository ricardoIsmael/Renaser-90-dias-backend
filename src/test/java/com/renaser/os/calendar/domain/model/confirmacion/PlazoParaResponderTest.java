package com.renaser.os.calendar.domain.model.confirmacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** E-550: el corte es «comienzo del día del evento − 12 h» en la zona del evento. */
class PlazoParaResponderTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId TOKIO = ZoneId.of("Asia/Tokyo");

    @Test
    @DisplayName("Lima de día: el corte es las 12:00 de Lima del día anterior (00:00 local − 12 h)")
    void limaDeDia() {
        Instant ahora = Instant.parse("2026-09-10T15:00:00Z");
        assertThat(PlazoParaResponder.yaVencio(Instant.parse("2026-09-09T16:59:00Z"), ahora, LIMA)).isTrue();
        assertThat(PlazoParaResponder.yaVencio(Instant.parse("2026-09-09T17:00:00Z"), ahora, LIMA)).isFalse();
    }

    @Test
    @DisplayName("Lima 19:00-24:00 (ya es mañana en UTC): el corte NO salta 24 h")
    void limaDeNocheNoSaltaUnDia() {
        Instant ahora = Instant.parse("2026-09-11T02:00:00Z"); // 21:00 del 10/09 en Lima
        assertThat(PlazoParaResponder.yaVencio(Instant.parse("2026-09-10T10:00:00Z"), ahora, LIMA)).isFalse();
        assertThat(PlazoParaResponder.yaVencio(Instant.parse("2026-09-09T16:59:00Z"), ahora, LIMA)).isTrue();
    }

    @Test
    @DisplayName("Los Ángeles de noche: su hoy sigue siendo el 9/09 aunque en UTC ya sea el 10/09")
    void losAngelesDeNoche() {
        Instant ahora = Instant.parse("2026-09-10T03:00:00Z"); // 20:00 del 9/09 en Los Ángeles
        assertThat(PlazoParaResponder.yaVencio(Instant.parse("2026-09-09T08:00:00Z"), ahora, LOS_ANGELES)).isFalse();
    }

    @Test
    @DisplayName("Tokio de madrugada: su hoy ya es el 11/09 aunque en UTC todavía sea el 10/09")
    void tokioDeMadrugada() {
        Instant ahora = Instant.parse("2026-09-10T20:00:00Z"); // 05:00 del 11/09 en Tokio
        assertThat(PlazoParaResponder.yaVencio(Instant.parse("2026-09-10T00:00:00Z"), ahora, TOKIO)).isTrue();
        assertThat(PlazoParaResponder.yaVencio(Instant.parse("2026-09-10T10:00:00Z"), ahora, TOKIO)).isFalse();
    }
}

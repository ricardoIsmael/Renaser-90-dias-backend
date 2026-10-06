package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class JornadaDelDiaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId TOKIO = ZoneId.of("Asia/Tokyo");

    @Test
    @DisplayName("a las 05:02 UTC del 9/11 es el 9/11 para Lima, el 8/11 para Los Angeles, y el 9/11 para Madrid y Tokio")
    void elDiaDependeDeLaZona() {
        Instant ahora = Instant.parse("2026-11-09T05:02:00Z");

        assertThat(JornadaDelDia.de(LIMA, ahora).hoy()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(JornadaDelDia.de(LOS_ANGELES, ahora).hoy()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(JornadaDelDia.de(MADRID, ahora).hoy()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(JornadaDelDia.de(TOKIO, ahora).hoy()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    @DisplayName("de madrugada UTC (00:00 a 05:00) toda America sigue en el dia anterior")
    void deMadrugadaUtcAmericaVaUnDiaAtras() {
        Instant ahora = Instant.parse("2026-11-09T03:30:00Z");

        assertThat(JornadaDelDia.de(LIMA, ahora).hoy()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(JornadaDelDia.de(LOS_ANGELES, ahora).hoy()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(JornadaDelDia.de(TOKIO, ahora).hoy()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    @DisplayName("el dia acaba de empezar a las 00:02 de Lima (05:02 UTC) y a las 01:02, pero no a las 02:00 ni mas tarde")
    void acabaDeEmpezarSoloLasPrimerasHoras() {
        assertThat(JornadaDelDia.de(LIMA, Instant.parse("2026-11-09T05:02:00Z")).acabaDeEmpezar()).isTrue();
        assertThat(JornadaDelDia.de(LIMA, Instant.parse("2026-11-09T06:02:00Z")).acabaDeEmpezar()).isTrue();
        assertThat(JornadaDelDia.de(LIMA, Instant.parse("2026-11-09T07:00:00Z")).acabaDeEmpezar()).isFalse();
        assertThat(JornadaDelDia.de(LIMA, Instant.parse("2026-11-09T15:00:00Z")).acabaDeEmpezar()).isFalse();
    }

    @Test
    @DisplayName("la misma regla vale en la zona de cada uno: 08:02 UTC es el amanecer de Los Angeles, no el de Lima")
    void elAmanecerEsLocal() {
        Instant ahora = Instant.parse("2026-11-09T08:02:00Z");

        assertThat(JornadaDelDia.de(LOS_ANGELES, ahora).acabaDeEmpezar()).isTrue();
        assertThat(JornadaDelDia.de(LIMA, ahora).acabaDeEmpezar()).isFalse();
        assertThat(JornadaDelDia.de(LOS_ANGELES, ahora).horaLocal()).isEqualTo(LocalTime.of(0, 2));
    }

    @Test
    @DisplayName("correrla dos veces da lo mismo")
    void esDerivada() {
        Instant ahora = Instant.parse("2026-11-09T05:02:00Z");

        assertThat(JornadaDelDia.de(LIMA, ahora)).isEqualTo(JornadaDelDia.de(LIMA, ahora));
    }
}

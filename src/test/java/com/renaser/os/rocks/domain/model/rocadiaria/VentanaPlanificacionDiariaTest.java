package com.renaser.os.rocks.domain.model.rocadiaria;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class VentanaPlanificacionDiariaTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    @Test
    void antesDeLas18EstaCerrada() {
        assertThat(VentanaPlanificacionDiaria.abierta(Instant.parse("2026-08-24T17:59:00Z"), UTC)).isFalse();
    }

    @Test
    void desdeLas18EstaAbierta() {
        assertThat(VentanaPlanificacionDiaria.abierta(Instant.parse("2026-08-24T18:00:00Z"), UTC)).isTrue();
    }

    @Test
    void hastaElFinalDelDiaSigueAbierta() {
        assertThat(VentanaPlanificacionDiaria.abierta(Instant.parse("2026-08-24T23:59:00Z"), UTC)).isTrue();
    }

    @Test
    void respetaLaZonaHorariaDelParticipante() {
        ZoneId limaMenos5 = ZoneId.of("America/Lima");
        // 22:30 UTC == 17:30 en Lima -> todavia no son las 18:00 locales.
        assertThat(VentanaPlanificacionDiaria.abierta(Instant.parse("2026-08-24T22:30:00Z"), limaMenos5)).isFalse();
        // 23:05 UTC == 18:05 en Lima -> ya abrio.
        assertThat(VentanaPlanificacionDiaria.abierta(Instant.parse("2026-08-24T23:05:00Z"), limaMenos5)).isTrue();
    }
}

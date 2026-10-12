package com.renaser.os.habits.domain.model.audiencia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistorialDeRolTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final CodigoDeRol APRENDIZ = CodigoDeRol.of("TRAINEE");
    private static final CodigoDeRol MENTOR = CodigoDeRol.of("MENTOR");

    @Test
    @DisplayName("sin historial rige el rol actual, para cualquier día")
    void sinHistorialRigeElActual() {
        assertThat(HistorialDeRol.vacio().vigenteEl(LocalDate.parse("2026-10-12"), LIMA, MENTOR)).isEqualTo(MENTOR);
    }

    @Test
    @DisplayName("cambio de rol hoy: hoy sigue el anterior, mañana el nuevo")
    void cambioDeRolHoy() {
        var historial = HistorialDeRol.de(List.of(new TramoDeRol(APRENDIZ, TramoDeRol.DESDE_SIEMPRE),
                new TramoDeRol(MENTOR, Instant.parse("2026-10-12T15:00:00Z"))));

        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-12"), LIMA, MENTOR)).isEqualTo(APRENDIZ);
        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-13"), LIMA, MENTOR)).isEqualTo(MENTOR);
        assertThat(historial.vigenteEl(LocalDate.parse("2026-09-01"), LIMA, MENTOR)).isEqualTo(APRENDIZ);
    }

    @Test
    @DisplayName("un cambio a las 04:30 UTC es del día local anterior en Lima")
    void cambioDeMadrugadaUtc() {
        var historial = HistorialDeRol.de(List.of(new TramoDeRol(APRENDIZ, TramoDeRol.DESDE_SIEMPRE),
                new TramoDeRol(MENTOR, Instant.parse("2026-10-13T04:30:00Z"))));

        assertThat(historial.vigenteEl(LocalDate.parse("2026-10-13"), LIMA, MENTOR)).isEqualTo(MENTOR);
    }
}

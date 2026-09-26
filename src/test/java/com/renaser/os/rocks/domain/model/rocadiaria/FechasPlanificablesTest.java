package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-177: la regla se movio de {@code RocaDiariaService} sin cambiar. Programa iniciado el martes
 * 2026-09-01: la semana 4 va del lunes 21 al domingo 27.
 */
class FechasPlanificablesTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 23);

    @Test
    @DisplayName("con la ventana nocturna abierta: de manana al domingo de la semana")
    void enPlazo() {
        FechasPlanificables fechas = FechasPlanificables.para(MIERCOLES, EstadoPlazo.EN_PLAZO, INICIO);

        assertThat(fechas).isEqualTo(new FechasPlanificables(MIERCOLES.plusDays(1), LocalDate.of(2026, 9, 27)));
        assertThat(fechas.contiene(MIERCOLES)).isFalse();
        assertThat(fechas.contiene(LocalDate.of(2026, 9, 27))).isTrue();
        assertThat(fechas.contiene(LocalDate.of(2026, 9, 28))).isFalse();
    }

    @Test
    @DisplayName("antes de las 18:00: hoy tambien")
    void aDestiempoIncluyeHoy() {
        assertThat(FechasPlanificables.para(MIERCOLES, EstadoPlazo.A_DESTIEMPO, INICIO).contiene(MIERCOLES)).isTrue();
    }
}

package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-177: la regla se movio de {@code RocaDiariaService}. D-192: la semana de programa es un bloque de
 * siete dias del programa. Programa iniciado el martes 2026-09-01: el miercoles 23 es el dia 23, la
 * semana 4 va del dia 22 (martes 22) al 28 (lunes 28).
 */
class FechasPlanificablesTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 23);
    private static final SemanaPrograma SEMANAS = SemanaPrograma.desde(INICIO, 23, MIERCOLES);

    @Test
    @DisplayName("con la ventana nocturna abierta: de manana al ultimo dia de la semana de programa")
    void enPlazo() {
        FechasPlanificables fechas = FechasPlanificables.para(MIERCOLES, EstadoPlazo.EN_PLAZO, SEMANAS);

        assertThat(fechas).isEqualTo(new FechasPlanificables(MIERCOLES.plusDays(1), LocalDate.of(2026, 9, 28)));
        assertThat(fechas.contiene(MIERCOLES)).isFalse();
        assertThat(fechas.contiene(LocalDate.of(2026, 9, 28))).isTrue();
        assertThat(fechas.contiene(LocalDate.of(2026, 9, 29))).isFalse();
    }

    @Test
    @DisplayName("antes de las 18:00: hoy tambien")
    void aDestiempoIncluyeHoy() {
        assertThat(FechasPlanificables.para(MIERCOLES, EstadoPlazo.A_DESTIEMPO, SEMANAS).contiene(MIERCOLES)).isTrue();
    }

    @Test
    @DisplayName("el dia 90 ya no ofrece manana: el programa termina ese dia")
    void elDiaNoventaNoOfreceManana() {
        LocalDate dia90 = INICIO.plusDays(89);
        FechasPlanificables fechas = FechasPlanificables.para(dia90, EstadoPlazo.EN_PLAZO,
                SemanaPrograma.desde(INICIO, 90, dia90));

        assertThat(fechas.contiene(dia90.plusDays(1))).isFalse();
    }
}

package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-177: la regla se movio de {@code RocaDiariaService}. D-203: la semana es de lunes a domingo. Programa
 * iniciado el martes 2026-09-01: el miercoles 23 es el dia 23, en la semana 4 (lunes 21 a domingo 27).
 *
 * <p><b>Corregido 2026-09-27 (D-203).</b> Con D-192 la semana 4 iba del martes 22 al lunes 28 y el corte
 * era el lunes 28; volvio a ser el domingo 27, como en produccion.
 */
class FechasPlanificablesTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 23);
    private static final SemanaPrograma SEMANAS = SemanaPrograma.desde(INICIO);

    @Test
    @DisplayName("con la ventana nocturna abierta: de manana al domingo de la semana")
    void enPlazo() {
        FechasPlanificables fechas = FechasPlanificables.para(MIERCOLES, EstadoPlazo.EN_PLAZO, SEMANAS);

        assertThat(fechas).isEqualTo(new FechasPlanificables(MIERCOLES.plusDays(1), LocalDate.of(2026, 9, 27)));
        assertThat(fechas.contiene(MIERCOLES)).isFalse();
        assertThat(fechas.contiene(LocalDate.of(2026, 9, 27))).isTrue();
        assertThat(fechas.contiene(LocalDate.of(2026, 9, 28))).isFalse();
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
        FechasPlanificables fechas = FechasPlanificables.para(dia90, EstadoPlazo.EN_PLAZO, SEMANAS);

        assertThat(fechas.contiene(dia90.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("inicio en miercoles: en la semana 13 el corte es el dia 90, el lunes despues de su domingo")
    void enLaTreceDeQuienEmpezoUnMiercolesElCorteEsElDiaNoventa() {
        SemanaPrograma semanas = SemanaPrograma.desde(LocalDate.of(2026, 9, 9)); // dia 90: lunes 2026-12-07
        FechasPlanificables fechas = FechasPlanificables.para(LocalDate.of(2026, 12, 4), EstadoPlazo.EN_PLAZO, semanas);

        assertThat(fechas).isEqualTo(new FechasPlanificables(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 7)));
    }

    @Test
    @DisplayName("inicio en lunes: en la semana 13 el corte es el sabado del dia 90, no el domingo 91")
    void enLaTreceDeQuienEmpezoUnLunesElCorteEsElSabado() {
        SemanaPrograma semanas = SemanaPrograma.desde(LocalDate.of(2026, 9, 7)); // dia 90: sabado 2026-12-05
        FechasPlanificables fechas = FechasPlanificables.para(LocalDate.of(2026, 12, 2), EstadoPlazo.EN_PLAZO, semanas);

        assertThat(fechas.hasta()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(fechas.contiene(LocalDate.of(2026, 12, 6))).isFalse();
    }
}

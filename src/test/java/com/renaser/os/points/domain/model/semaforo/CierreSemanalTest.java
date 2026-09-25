package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ConteoDelDia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CierreSemanalTest {

    private static final LocalDate SABADO_26 = LocalDate.of(2026, 9, 26);
    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);
    private static final LocalDate VIERNES_18 = LocalDate.of(2026, 9, 18);
    private static final LocalDate VIERNES_11 = LocalDate.of(2026, 9, 11);

    /** Día 1 el martes 8 de septiembre; día 90 el 6 de diciembre. */
    private static final CalendarioDeMedicion PROGRAMA =
            new CalendarioDeMedicion(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 12, 6), List.of());

    @Test
    void elSabadoSeCierraLaSemanaQueTerminoAyer() {
        assertThat(CierreSemanal.semanasPorCerrar(SABADO_26, VIERNES_18, PROGRAMA)).containsExactly(VIERNES_25);
    }

    @Test
    void elViernesTodaviaNoSeCierraLaSemanaEnCurso() {
        assertThat(CierreSemanal.semanasPorCerrar(VIERNES_25, VIERNES_18, PROGRAMA)).isEmpty();
    }

    /** Si el backend estuvo caído, la próxima corrida cierra todo lo que quedó abierto, en orden. */
    @Test
    void unaCorridaTardeCierraLasSemanasQueFaltan() {
        assertThat(CierreSemanal.semanasPorCerrar(SABADO_26, null, PROGRAMA))
                .containsExactly(VIERNES_11, VIERNES_18, VIERNES_25);
    }

    @Test
    void laPrimeraSemanaPuedeSerParcial() {
        assertThat(CierreSemanal.semanasPorCerrar(LocalDate.of(2026, 9, 12), null, PROGRAMA))
                .containsExactly(VIERNES_11);
    }

    @Test
    void despuesDelDiaNoventaNoQuedanSemanasPorCerrar() {
        LocalDate ultimoViernesDelPrograma = LocalDate.of(2026, 12, 11);
        assertThat(CierreSemanal.semanasPorCerrar(LocalDate.of(2027, 1, 30), ultimoViernesDelPrograma, PROGRAMA))
                .isEmpty();
    }

    @Test
    void losDiasAbiertosVanDesdeElSabadoPosteriorAlUltimoCierreHastaAyer() {
        assertThat(CierreSemanal.primerDiaAbierto(VIERNES_18, PROGRAMA)).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(CierreSemanal.primerDiaAbierto(null, PROGRAMA)).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(CierreSemanal.ultimoDiaCerrado(SABADO_26, PROGRAMA)).isEqualTo(VIERNES_25);
        assertThat(CierreSemanal.ultimoDiaCerrado(LocalDate.of(2027, 1, 1), PROGRAMA))
                .isEqualTo(LocalDate.of(2026, 12, 6));
    }

    @Test
    void elAvisoSaleSoloElFinDeSemanaDelCierre() {
        assertThat(CierreSemanal.correspondeAvisar(VIERNES_25, SABADO_26)).isTrue();
        assertThat(CierreSemanal.correspondeAvisar(VIERNES_25, LocalDate.of(2026, 9, 27))).isTrue();
        assertThat(CierreSemanal.correspondeAvisar(VIERNES_25, LocalDate.of(2026, 9, 28))).isFalse();
    }

    @Test
    void elPlanCombinaHabitosYObjetivosYGuardaLosDiasVacios() {
        PlanDeCierre plan = PlanDeCierre.para(PROGRAMA, SABADO_26, VIERNES_18);
        LocalDate lunes = LocalDate.of(2026, 9, 21);

        Map<LocalDate, CumplimientoDelDia> dias = plan.diasMedidos(
                List.of(new ConteoDelDia(lunes, 5, 5)), List.of(new ConteoDelDia(lunes, 3, 1)));

        assertThat(plan.desde()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(plan.hasta()).isEqualTo(VIERNES_25);
        assertThat(dias).hasSize(7);
        assertThat(dias.get(lunes).porcentaje()).contains(75);
        assertThat(dias.get(VIERNES_25).conDatos()).isFalse();
        assertThat(plan.tieneTrabajo()).isTrue();
    }

    /** Activó el programa eligiendo un Día 1 que todavía no llegó: nada que medir ni cerrar. */
    @Test
    void unProgramaQueTodaviaNoEmpiezaNoTieneTrabajo() {
        CalendarioDeMedicion futuro =
                new CalendarioDeMedicion(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 12, 26), List.of());

        PlanDeCierre plan = PlanDeCierre.para(futuro, SABADO_26, null);

        assertThat(plan.tieneTrabajo()).isFalse();
        assertThat(plan.diasMedidos(List.of(), List.of())).isEmpty();
    }

    @Test
    void justoDespuesDelCierreNoHayNadaQueHacer() {
        PlanDeCierre plan = PlanDeCierre.para(PROGRAMA, SABADO_26, VIERNES_25);

        assertThat(plan.hayDiasPorCalcular()).isFalse();
        assertThat(plan.tieneTrabajo()).isFalse();
    }
}

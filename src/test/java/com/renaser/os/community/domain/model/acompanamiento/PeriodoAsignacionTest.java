package com.renaser.os.community.domain.model.acompanamiento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Intervalos semiabiertos [inicio, fin). Es la pieza que impide atribuir dos mentores al
 * mismo instante: si el cierre de A fuera inclusivo, el instante del relevo pertenecería a
 * los dos y la evaluación contaría la misma obligación dos veces (plan.md §3).
 */
class PeriodoAsignacionTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    @DisplayName("el instante de cierre ya no pertenece al periodo")
    void cierreEsExclusivo() {
        PeriodoAsignacion periodo = PeriodoAsignacion.cerrado(T0, T1);

        assertThat(periodo.contiene(T0)).isTrue();
        assertThat(periodo.contiene(T1.minusMillis(1))).isTrue();
        assertThat(periodo.contiene(T1)).isFalse();
    }

    @Test
    @DisplayName("un periodo abierto contiene todo instante desde su inicio")
    void abiertoNoTermina() {
        PeriodoAsignacion periodo = PeriodoAsignacion.abierto(T0);

        assertThat(periodo.vigente()).isTrue();
        assertThat(periodo.contiene(T0)).isTrue();
        assertThat(periodo.contiene(T1.plusSeconds(86_400))).isTrue();
        assertThat(periodo.contiene(T0.minusMillis(1))).isFalse();
    }

    @Test
    @DisplayName("el relevo exacto no solapa: A cierra donde B abre")
    void relevoExactoNoSolapa() {
        PeriodoAsignacion saliente = PeriodoAsignacion.cerrado(T0, T1);
        PeriodoAsignacion entrante = PeriodoAsignacion.abierto(T1);

        assertThat(saliente.solapaCon(entrante)).isFalse();
        assertThat(entrante.solapaCon(saliente)).isFalse();
    }

    @Test
    @DisplayName("un milisegundo de superposición ya es solape")
    void superposicionMinimaSolapa() {
        PeriodoAsignacion saliente = PeriodoAsignacion.cerrado(T0, T1.plusMillis(1));
        PeriodoAsignacion entrante = PeriodoAsignacion.abierto(T1);

        assertThat(saliente.solapaCon(entrante)).isTrue();
    }

    @Test
    @DisplayName("dos periodos abiertos sobre la misma linea siempre solapan")
    void dosAbiertosSolapan() {
        assertThat(PeriodoAsignacion.abierto(T0).solapaCon(PeriodoAsignacion.abierto(T1))).isTrue();
    }

    @Test
    @DisplayName("un periodo de duracion cero no es valido")
    void duracionCeroEsInvalida() {
        assertThatThrownBy(() -> PeriodoAsignacion.cerrado(T0, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PeriodoAsignacion.cerrado(T1, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la interseccion con la ventana evaluada recorta, no extiende")
    void interseccionRecorta() {
        PeriodoAsignacion asignacion = PeriodoAsignacion.cerrado(
                Instant.parse("2026-09-10T00:00:00Z"), Instant.parse("2026-09-20T00:00:00Z"));

        PeriodoAsignacion mes = PeriodoAsignacion.cerrado(T0, T1);

        assertThat(asignacion.interseccionCon(mes)).contains(asignacion);

        PeriodoAsignacion mesAnterior = PeriodoAsignacion.cerrado(
                Instant.parse("2026-08-01T00:00:00Z"), T0);
        assertThat(asignacion.interseccionCon(mesAnterior)).isEmpty();
    }

    @Test
    @DisplayName("cerrar un periodo antes de su inicio es un error, no un intervalo negativo")
    void cerrarAntesDelInicioFalla() {
        PeriodoAsignacion abierto = PeriodoAsignacion.abierto(T1);

        assertThatThrownBy(() -> abierto.cerrarEn(T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.renaser.os.rocks.domain.model.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgresoSemanalTest {

    @Test
    @DisplayName("sin nada planificado todavia -> 0, no 100 (a diferencia de PorcentajeRocas)")
    void sinPlanificadoEsCero() {
        assertThat(ProgresoSemanal.calcular(0, 0)).isZero();
    }

    @Test
    @DisplayName("redondea al entero mas cercano")
    void redondeaAlEnteroMasCercano() {
        assertThat(ProgresoSemanal.calcular(3, 2)).isEqualTo(67); // 66.67 -> 67
        assertThat(ProgresoSemanal.calcular(4, 1)).isEqualTo(25);
    }

    @Test
    @DisplayName("todo completado -> 100")
    void todoCompletadoEsCien() {
        assertThat(ProgresoSemanal.calcular(5, 5)).isEqualTo(100);
    }

    @Test
    void completadoFueraDeRangoRechazado() {
        assertThatThrownBy(() -> ProgresoSemanal.calcular(3, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}

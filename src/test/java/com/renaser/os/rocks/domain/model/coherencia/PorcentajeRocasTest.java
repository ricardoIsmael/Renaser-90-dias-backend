package com.renaser.os.rocks.domain.model.coherencia;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ley VI para Rocas Diarias — mismos casos borde que
 * {@code EscalaPuntosRocaTest} exige para la escala de puntos: la fórmula
 * completa, no solo el camino feliz.
 */
class PorcentajeRocasTest {

    private static final LocalDate D1 = LocalDate.of(2026, 8, 20);
    private static final LocalDate D2 = LocalDate.of(2026, 8, 21);
    private static final LocalDate D3 = LocalDate.of(2026, 8, 22);

    @Test
    void ventanaSinDiasCalificablesDevuelve100() {
        assertThat(PorcentajeRocas.calcular(List.of())).isEqualByComparingTo("100.0");
        assertThat(PorcentajeRocas.calcular(null)).isEqualByComparingTo("100.0");
    }

    @Test
    void unSoloDiaDevuelveElPuntajeDeEseDia() {
        BigDecimal resultado = PorcentajeRocas.calcular(List.of(new DiaRocas(D1, 3, 3)));
        assertThat(resultado).isEqualByComparingTo("100.0");
    }

    @Test
    void dobleRedondeoDeliberado_promedioDeDiasYaRedondeados() {
        // día 1: 1/3 -> 33.33... redondea a 33
        // día 2: 1/2 -> 50.0    redondea a 50
        // promedio de los DÍAS ya redondeados: (33 + 50) / 2 = 41.5
        // (el promedio CONTINUO sería (1+1)/(3+2) = 40.0 — números distintos:
        //  si esto diera 40.0 en vez de 41.5, el doble redondeo se rompió)
        BigDecimal resultado = PorcentajeRocas.calcular(List.of(
                new DiaRocas(D1, 3, 1),
                new DiaRocas(D2, 2, 1)));
        assertThat(resultado).isEqualByComparingTo("41.5");
    }

    @Test
    void promedioDeVariosDiasConUnDecimalDePrecision() {
        // 33 + 67 + 100 = 200 / 3 = 66.666... -> redondeado a 1 decimal: 66.7
        BigDecimal resultado = PorcentajeRocas.calcular(List.of(
                new DiaRocas(D1, 3, 1),   // 33
                new DiaRocas(D2, 3, 2),   // 67
                new DiaRocas(D3, 3, 3))); // 100
        assertThat(resultado).isEqualByComparingTo("66.7");
    }

    @Test
    void diaSinNingunaCompletadaCuentaComoCero_noSeExcluye() {
        BigDecimal resultado = PorcentajeRocas.calcular(List.of(new DiaRocas(D1, 3, 0)));
        assertThat(resultado).isEqualByComparingTo("0.0");
    }
}

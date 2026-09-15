package com.renaser.os.rocks.domain.model.coherencia;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La coherencia (D-128, 2026-09-15): acciones diarias CUMPLIDAS sobre PLANIFICADAS en la ventana.
 *
 * <p>Esta clase cambio entera ese dia. Antes verificaba dos cosas que hoy son falsas: que una
 * ventana sin datos valia <b>100.0</b> —lo que hacia que la app dijera "100 % · Nivel de
 * excelencia" a quien nunca planifico nada— y que el resultado era el <b>promedio de los
 * porcentajes de cada dia</b> ya redondeados, con lo que un dia de 1/1 pesaba igual que uno de
 * 3/3. Se dejan a la vista los dos casos viejos en los tests de abajo, con los numeros que daban.
 */
class PorcentajeRocasTest {

    private static final LocalDate D1 = LocalDate.of(2026, 8, 20);
    private static final LocalDate D2 = LocalDate.of(2026, 8, 21);
    private static final LocalDate D3 = LocalDate.of(2026, 8, 22);

    /** Antes devolvia 100.0. Ese 100 es el que la app mostraba como "Nivel de excelencia". */
    @Test
    void ventanaSinNingunaAccionPlanificadaNoTienePorcentaje() {
        assertThat(PorcentajeRocas.calcular(List.of())).isEmpty();
        assertThat(PorcentajeRocas.calcular(null)).isEmpty();
    }

    @Test
    void unSoloDiaTodoCumplidoEsCien() {
        assertThat(PorcentajeRocas.calcular(List.of(new DiaRocas(D1, 3, 3))))
                .hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("100.0"));
    }

    @Test
    void diaPlanificadoYSinCumplirEsCero_yEsDistintoDeNoTenerDato() {
        assertThat(PorcentajeRocas.calcular(List.of(new DiaRocas(D1, 3, 0))))
                .hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("0.0"));
        assertThat(PorcentajeRocas.calcular(List.of())).isEmpty();
    }

    /**
     * Se cuentan ACCIONES, no dias. Con el promedio por dia que habia antes esto daba <b>41.5</b>
     * —(33 + 50) / 2, cada dia ya redondeado— y ahora da 40.0: 2 cumplidas sobre 5 planificadas.
     * El dia de tres acciones pesa mas porque se planificaron mas cosas.
     */
    @Test
    void cuentaAccionesYNoPromediaDias() {
        assertThat(PorcentajeRocas.calcular(List.of(
                new DiaRocas(D1, 3, 1),
                new DiaRocas(D2, 2, 1))))
                .hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("40.0"));
    }

    /** 6 cumplidas sobre 9 planificadas = 66.7 (un decimal). Con el promedio viejo daba 66.7 tambien,
     * por casualidad: los tres dias tenian el mismo total. */
    @Test
    void redondeaAUnDecimal() {
        assertThat(PorcentajeRocas.calcular(List.of(
                new DiaRocas(D1, 3, 1),
                new DiaRocas(D2, 3, 2),
                new DiaRocas(D3, 3, 3))))
                .hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("66.7"));
    }
}

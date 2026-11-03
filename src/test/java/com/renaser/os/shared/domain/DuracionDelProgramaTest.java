package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuracionDelProgramaTest {

    @Test
    @DisplayName("La duración va de 1 a 365 días")
    void validaElRango() {
        assertThat(DuracionDelPrograma.validar(1)).isEqualTo(1);
        assertThat(DuracionDelPrograma.validar(90)).isEqualTo(90);
        assertThat(DuracionDelPrograma.validar(365)).isEqualTo(365);
        assertThatThrownBy(() -> DuracionDelPrograma.validar(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DuracionDelPrograma.validar(366)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El objetivo grande se nombra con el plazo del programa de la persona: 90 días o 1 año")
    void plazoDelObjetivo() {
        assertThat(DuracionDelPrograma.plazoDelObjetivo(90)).isEqualTo("90 días");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(365)).isEqualTo("1 año");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(120)).isEqualTo("120 días");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(0)).as("sin dato, el de siempre").isEqualTo("90 días");
    }

    @Test
    @DisplayName("Sabiendo la formación: el Diplomado dice 3 meses o 1 año; la Escuela y la Certificación, 90 días")
    void plazoDelObjetivoConFormacion() {
        assertThat(DuracionDelPrograma.plazoDelObjetivo(90, Formacion.DIPLOMADO)).isEqualTo("3 meses");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(365, Formacion.DIPLOMADO)).isEqualTo("1 año");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(90, Formacion.ESCUELA)).isEqualTo("90 días");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(90, Formacion.CERTIFICACION)).isEqualTo("90 días");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(365, Formacion.ESCUELA)).isEqualTo("1 año");
        assertThat(DuracionDelPrograma.plazoDelObjetivo(90, null)).isEqualTo("90 días");
    }

    @Test
    @DisplayName("Con 90 días el día no cambia; con 365 se lleva a la escala de 90 sin pasarse de 90")
    void enEscalaBase() {
        assertThat(DuracionDelPrograma.enEscalaBase(37, 90)).isEqualTo(37);
        assertThat(DuracionDelPrograma.enEscalaBase(0, 365)).isZero();
        assertThat(DuracionDelPrograma.enEscalaBase(1, 365)).isEqualTo(1);
        assertThat(DuracionDelPrograma.enEscalaBase(365, 365)).isEqualTo(90);
        assertThat(DuracionDelPrograma.enEscalaBase(182, 365)).isEqualTo(45);
    }

    @Test
    @DisplayName("Los cortes de fase de un año caen en las fases de siempre al llevarlos a la escala de 90")
    void losCortesCaenEnLasFasesDeSiempre() {
        for (int fase = 1; fase <= 4; fase++) {
            int inicio = CortesDeFase.inicioDeFase(fase, 365);
            assertThat(CortesDeFase.numeroDeFase(DuracionDelPrograma.enEscalaBase(inicio, 365), 90)).isEqualTo(fase);
            if (inicio > 1) {
                assertThat(CortesDeFase.numeroDeFase(DuracionDelPrograma.enEscalaBase(inicio - 1, 365), 90)).isEqualTo(fase - 1);
            }
        }
    }

    @Test
    @DisplayName("La etiqueta dice «3 meses», «1 año» o los días")
    void etiqueta() {
        assertThat(DuracionDelPrograma.etiqueta(90)).isEqualTo("3 meses");
        assertThat(DuracionDelPrograma.etiqueta(365)).isEqualTo("1 año");
        assertThat(DuracionDelPrograma.etiqueta(100)).isEqualTo("100 días");
    }
}

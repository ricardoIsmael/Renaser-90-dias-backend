package com.renaser.os.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El único lugar donde se acota el tamaño de una lista (spec 050, tramo F; FR-034). Sin Spring. */
class PaginaAcotadaTest {

    // spec 050 FR-034
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 201, 100_000, Integer.MAX_VALUE, Integer.MIN_VALUE})
    @DisplayName("estricta: un tamaño fuera de 1..máximo es un 400 con el motivo, como GET /admin/pagos/cuotas")
    void estrictaRechazaLoQueSePasa(int tamano) {
        assertThatThrownBy(() -> PaginaAcotada.estricta(0, tamano, 200))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size debe estar entre 1 y 200");
    }

    // spec 050 FR-034
    @Test
    @DisplayName("estricta: los límites mismos valen (1 y el máximo) y la página negativa es un 400")
    void estrictaAceptaLosLimites() {
        assertThat(PaginaAcotada.estricta(0, 1, 200)).isEqualTo(new PaginaAcotada(0, 1));
        assertThat(PaginaAcotada.estricta(7, 200, 200).tamano()).isEqualTo(200);
        assertThatThrownBy(() -> PaginaAcotada.estricta(-1, 20, 200))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("page no puede ser negativa");
    }

    // spec 050 FR-034
    @Test
    @DisplayName("recortada: lleva el tamaño a 1..máximo y la página a 0 sin fallar (las listas que ya recortaban en silencio)")
    void recortadaNuncaFalla() {
        assertThat(PaginaAcotada.recortada(-5, 100_000, 200)).isEqualTo(new PaginaAcotada(0, 200));
        assertThat(PaginaAcotada.recortada(3, 0, 200)).isEqualTo(new PaginaAcotada(3, 1));
        assertThat(PaginaAcotada.recortada(3, Integer.MIN_VALUE, 200).tamano()).isEqualTo(1);
        assertThat(PaginaAcotada.recortada(3, 50, 200)).isEqualTo(new PaginaAcotada(3, 50));
    }

    @Test
    @DisplayName("desde() no desborda un int con una página enorme")
    void desdeNoDesborda() {
        assertThat(PaginaAcotada.estricta(Integer.MAX_VALUE, 200, 200).desde()).isEqualTo(Integer.MAX_VALUE * 200L);
        assertThat(PaginaAcotada.estricta(2, 30, 100).desde()).isEqualTo(60L);
    }

    @Test
    @DisplayName("un máximo menor que 1 es un error de quien declara la lista, no de quien la pide")
    void maximoInvalido() {
        assertThatThrownBy(() -> PaginaAcotada.estricta(0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaginaAcotada.recortada(0, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

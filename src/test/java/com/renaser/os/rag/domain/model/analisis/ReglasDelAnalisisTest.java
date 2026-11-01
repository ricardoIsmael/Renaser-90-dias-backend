package com.renaser.os.rag.domain.model.analisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cuánto es «datos suficientes» (spec 031, FR-015): un solo valor, los propuestos en la spec. */
class ReglasDelAnalisisTest {

    private final ReglasDelAnalisis reglas = ReglasDelAnalisis.POR_DEFECTO;

    @Test
    @DisplayName("una semana pide al menos 5 acciones en al menos 3 días distintos; un mes, 15 acciones")
    void minimos() {
        assertThat(reglas.alcanza(TipoDePeriodo.SEMANA, 5, 3)).isTrue();
        assertThat(reglas.alcanza(TipoDePeriodo.SEMANA, 4, 3)).isFalse();
        assertThat(reglas.alcanza(TipoDePeriodo.SEMANA, 12, 2)).isFalse();
        assertThat(reglas.alcanza(TipoDePeriodo.MES, 15, 1)).isTrue();
        assertThat(reglas.alcanza(TipoDePeriodo.MES, 14, 20)).isFalse();
    }

    @Test
    @DisplayName("dice qué falta, para que la hoja lo cuente sin culpa")
    void loQueFalta() {
        assertThat(reglas.loQueFalta(TipoDePeriodo.SEMANA, 1, 1)).containsExactly("ACCIONES", "DIAS");
        assertThat(reglas.loQueFalta(TipoDePeriodo.SEMANA, 6, 2)).containsExactly("DIAS");
        assertThat(reglas.loQueFalta(TipoDePeriodo.MES, 3, 3)).containsExactly("ACCIONES");
        assertThat(reglas.loQueFalta(TipoDePeriodo.SEMANA, 5, 3)).isEmpty();
    }

    @Test
    @DisplayName("un valor inválido no deja armar las reglas")
    void invalidas() {
        assertThatThrownBy(() -> new ReglasDelAnalisis(0, 3, 15, 3, 3, Duration.ofHours(6), 7, 500)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReglasDelAnalisis(5, 3, 15, 3, 3, Duration.ofHours(-1), 7, 500)).isInstanceOf(IllegalArgumentException.class);
    }
}

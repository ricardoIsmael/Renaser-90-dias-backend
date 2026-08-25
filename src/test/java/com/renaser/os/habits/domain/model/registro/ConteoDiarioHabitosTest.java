package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConteoDiarioHabitosTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);

    @Test
    void rechazaFechaNula() {
        assertThatThrownBy(() -> new ConteoDiarioHabitos(null, 1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaConteosNegativos() {
        assertThatThrownBy(() -> new ConteoDiarioHabitos(FECHA, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteoDiarioHabitos(FECHA, 1, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConteoDiarioHabitos(FECHA, 1, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaCompletadosMayorATotal() {
        assertThatThrownBy(() -> new ConteoDiarioHabitos(FECHA, 2, 3, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaOpcionalesNoCompletadosMayorATotal() {
        assertThatThrownBy(() -> new ConteoDiarioHabitos(FECHA, 2, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

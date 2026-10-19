package com.renaser.os.habits.domain.model.registro;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void contabaTodoMenosElOpcionalSinCompletar() {
        assertThat(ConteoDiarioHabitos.contaba(false, false)).as("obligatorio pendiente").isTrue();
        assertThat(ConteoDiarioHabitos.contaba(false, true)).as("obligatorio cumplido").isTrue();
        assertThat(ConteoDiarioHabitos.contaba(true, true)).as("opcional cumplido").isTrue();
        assertThat(ConteoDiarioHabitos.contaba(true, false)).as("opcional sin cumplir").isFalse();
    }

    /** La regla vista por registro suma lo mismo que la vista por dia: una sola definicion de «contaba». */
    @Test
    void sumarContabaDaLosCalificablesDelDia() {
        // obligatorio cumplido, obligatorio pendiente, opcional cumplido, opcional pendiente, opcional pendiente
        boolean[][] registros = {{false, true}, {false, false}, {true, true}, {true, false}, {true, false}};
        int total = registros.length;
        int opcionalesNoCompletados = 2;

        long contaron = java.util.Arrays.stream(registros).filter(r -> ConteoDiarioHabitos.contaba(r[0], r[1])).count();

        assertThat(contaron).isEqualTo(ConteoDiarioHabitos.calificables(total, opcionalesNoCompletados));
    }
}

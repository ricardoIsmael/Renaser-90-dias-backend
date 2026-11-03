package com.renaser.os.leadership.domain.model.habitos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumenDeUnHabitoTest {

    private static ResumenDeUnHabito habito(String titulo, int cumplieron, int pendientes, int sinCumplir) {
        return new ResumenDeUnHabito(UUID.randomUUID(), titulo, "CUERPO", "BODY", "ESCUELA", cumplieron, pendientes, sinCumplir);
    }

    @Test
    @DisplayName("lo esperado es la suma de los tres; quien no tenía nada que medir no está en ninguno")
    void esperados() {
        assertThat(habito("Agua", 7, 2, 1).esperados()).isEqualTo(10);
    }

    @Test
    @DisplayName("un hábito que a nadie le tocó ese día no tiene resumen (no aparece en la lista)")
    void nadieLeTocaba() {
        assertThatThrownBy(() -> habito("Agua", 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> habito("Agua", -1, 2, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los hábitos con menos cumplimiento van primero, comparando fracciones exactas (sin redondear)")
    void peorPrimero() {
        ResumenDeUnHabito tresDeDiez = habito("Z", 3, 3, 4);
        ResumenDeUnHabito nueveDeDoce = habito("A", 9, 1, 2);
        ResumenDeUnHabito ninguno = habito("M", 0, 0, 5);
        ResumenDeUnHabito todos = habito("B", 4, 0, 0);

        List<ResumenDeUnHabito> ordenados = List.of(todos, nueveDeDoce, tresDeDiez, ninguno).stream()
                .sorted(ResumenDeUnHabito.PEOR_PRIMERO).toList();

        assertThat(ordenados).containsExactly(ninguno, tresDeDiez, nueveDeDoce, todos);
    }

    @Test
    @DisplayName("a igual cumplimiento, por nombre (sin tildes ni mayúsculas) y, al final, por id: el orden es estable")
    void desempate() {
        ResumenDeUnHabito a = habito("Ánimo", 1, 1, 0);
        ResumenDeUnHabito b = habito("agua", 1, 1, 0);
        ResumenDeUnHabito c = habito("Zumo", 1, 1, 0);

        assertThat(List.of(c, a, b).stream().sorted(ResumenDeUnHabito.PEOR_PRIMERO).toList()).containsExactly(b, a, c);
        // 2 de 4 y 1 de 2 son la misma fracción
        assertThat(habito("X", 2, 1, 1).cumplimiento().compareTo(habito("X", 1, 1, 0).cumplimiento())).isZero();
    }
}

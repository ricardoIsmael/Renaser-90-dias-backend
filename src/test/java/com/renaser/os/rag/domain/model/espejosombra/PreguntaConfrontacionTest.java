package com.renaser.os.rag.domain.model.espejosombra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreguntaConfrontacionTest {

    @Test
    void aceptaOrdenEnRango() {
        PreguntaConfrontacion pregunta = new PreguntaConfrontacion(1, "Que evitaste esta semana?");
        assertThat(pregunta.orden()).isEqualTo(1);

        assertThat(new PreguntaConfrontacion(10, "otra").orden()).isEqualTo(10);
    }

    @Test
    void rechazaOrdenMenorA1() {
        assertThatThrownBy(() -> new PreguntaConfrontacion(0, "pregunta"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaOrdenMayorA10() {
        assertThatThrownBy(() -> new PreguntaConfrontacion(11, "pregunta"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaPreguntaVacia() {
        assertThatThrownBy(() -> new PreguntaConfrontacion(1, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PreguntaConfrontacion(1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

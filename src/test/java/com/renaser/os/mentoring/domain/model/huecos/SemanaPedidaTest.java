package com.renaser.os.mentoring.domain.model.huecos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanaPedidaTest {

    /** Miercoles 28 de octubre de 2026: la semana en curso empieza el lunes 26. */
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate EN_CURSO = LocalDate.of(2026, 10, 26);
    private static final LocalDate PRIMERA = LocalDate.of(2026, 10, 12);

    @Test
    @DisplayName("sin semana es la en curso de la persona")
    void sinSemana() {
        assertThat(SemanaPedida.resolver(null, HOY, PRIMERA)).isEqualTo(EN_CURSO);
        assertThat(SemanaPedida.resolver(null, HOY, null)).isEqualTo(EN_CURSO);
    }

    @Test
    @DisplayName("acepta la en curso, la primera y las de en medio")
    void aceptadas() {
        assertThat(SemanaPedida.resolver(EN_CURSO, HOY, PRIMERA)).isEqualTo(EN_CURSO);
        assertThat(SemanaPedida.resolver(PRIMERA, HOY, PRIMERA)).isEqualTo(PRIMERA);
        assertThat(SemanaPedida.resolver(PRIMERA.plusWeeks(1), HOY, PRIMERA)).isEqualTo(PRIMERA.plusWeeks(1));
    }

    @Test
    @DisplayName("una semana futura es un error")
    void futura() {
        assertThatThrownBy(() -> SemanaPedida.resolver(EN_CURSO.plusWeeks(1), HOY, PRIMERA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("todavia no llega");
    }

    @Test
    @DisplayName("una semana anterior a la primera de su programa es un error")
    void anterior() {
        assertThatThrownBy(() -> SemanaPedida.resolver(PRIMERA.minusWeeks(1), HOY, PRIMERA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("anterior a la primera");
    }

    @Test
    @DisplayName("sin Dia 1 elegido la unica semana posible es la en curso")
    void sinDiaUno() {
        assertThatThrownBy(() -> SemanaPedida.resolver(EN_CURSO.minusWeeks(1), HOY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SemanaPedida.resolver(EN_CURSO, HOY, null)).isEqualTo(EN_CURSO);
    }

    @Test
    @DisplayName("lo que no es lunes se rechaza")
    void noEsLunes() {
        assertThatThrownBy(() -> SemanaPedida.resolver(EN_CURSO.plusDays(1), HOY, PRIMERA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lunes");
    }
}

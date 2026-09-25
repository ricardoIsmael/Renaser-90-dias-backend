package com.renaser.os.rag.domain.model.agenda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.EnumSet;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiasDeSemanaTest {

    @Test
    @DisplayName("entiende rangos, listas, 'fin de semana' y 'todos', con o sin tildes")
    void formas() {
        assertThat(DiasDeSemana.leer("lunes-viernes")).isEqualTo(EnumSet.range(MONDAY, FRIDAY));
        assertThat(DiasDeSemana.leer("Lunes a Viernes")).isEqualTo(EnumSet.range(MONDAY, FRIDAY));
        assertThat(DiasDeSemana.leer("lunes, Miércoles")).containsExactly(MONDAY, WEDNESDAY);
        assertThat(DiasDeSemana.leer("fin de semana")).containsExactly(SATURDAY, SUNDAY);
        assertThat(DiasDeSemana.leer("todos")).isEqualTo(EnumSet.allOf(DayOfWeek.class));
        assertThat(DiasDeSemana.leer("sabado-lunes")).containsExactlyInAnyOrder(SATURDAY, SUNDAY, MONDAY);
    }

    @Test
    @DisplayName("un dia que no existe es IllegalArgumentException")
    void diaInvalido() {
        assertThatThrownBy(() -> DiasDeSemana.leer("lunes, feriado")).hasMessageContaining("feriado");
        assertThatThrownBy(() -> DiasDeSemana.leer(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}

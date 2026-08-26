package com.renaser.os.rocks.domain.model.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaGrillaSemanalTest {

    @Test
    @DisplayName("un dia futuro viaja con completadas/total en null")
    void diaFuturoConNulls() {
        var dia = new DiaGrillaSemanal(LocalDate.of(2026, 9, 1), DayOfWeek.TUESDAY, null, null, false);
        assertThat(dia.completadas()).isNull();
        assertThat(dia.total()).isNull();
    }

    @Test
    void fechaObligatoria() {
        assertThatThrownBy(() -> new DiaGrillaSemanal(null, DayOfWeek.MONDAY, 0, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

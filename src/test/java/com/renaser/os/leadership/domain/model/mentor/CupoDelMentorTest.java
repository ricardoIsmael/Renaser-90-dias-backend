package com.renaser.os.leadership.domain.model.mentor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El cupo del mentor y sus aprendices por color (spec 056, FR-003). */
class CupoDelMentorTest {

    @Test
    @DisplayName("spec 056 FR-003: el máximo es la suma de los topes de sus grupos")
    void sumaLosTopes() {
        assertThat(CupoDelMentor.de(9, List.of(10))).isEqualTo(new CupoDelMentor(9, 10));
        assertThat(CupoDelMentor.de(14, List.of(12, 10))).isEqualTo(new CupoDelMentor(14, 22));
    }

    @Test
    @DisplayName("spec 056 FR-004: sin grupos es 0 de 0")
    void sinGrupos() {
        assertThat(CupoDelMentor.de(0, List.of())).isEqualTo(new CupoDelMentor(0, 0));
    }

    @Test
    @DisplayName("spec 056 FR-003: un cupo o una cantidad de aprendices negativa no existe")
    void nadaNegativo() {
        assertThatThrownBy(() -> new CupoDelMentor(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AprendicesPorColor(0, -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("spec 056 FR-003: el total de aprendices por color suma los cuatro estados, los sin datos incluidos")
    void totalPorColor() {
        assertThat(new AprendicesPorColor(5, 0, 0, 4).total()).isEqualTo(9);
        assertThat(AprendicesPorColor.ninguno().total()).isZero();
    }
}

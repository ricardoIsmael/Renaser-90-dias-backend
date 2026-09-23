package com.renaser.os.habits.domain.model.eleccion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SemanaDeEleccionTest {

    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 24);

    @Test
    @DisplayName("se elige de hoy al domingo de esta semana, nunca un dia pasado")
    void deHoyAlDomingo() {
        assertThat(SemanaDeEleccion.diasElegibles(12, JUEVES)).containsExactly(
                JUEVES, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27));
    }

    @Test
    @DisplayName("un domingo solo queda el propio domingo")
    void elDomingo() {
        LocalDate domingo = LocalDate.of(2026, 9, 27);

        assertThat(SemanaDeEleccion.diasElegibles(12, domingo)).containsExactly(domingo);
    }

    @Test
    @DisplayName("el Dia 0 no elige ninguno")
    void diaCero() {
        assertThat(SemanaDeEleccion.diasElegibles(0, JUEVES)).isEmpty();
    }

    @Test
    @DisplayName("esElegible coincide con diasElegibles: ayer y el lunes siguiente quedan afuera")
    void coincideConLaLista() {
        assertThat(SemanaDeEleccion.esElegible(JUEVES.minusDays(1), JUEVES)).isFalse();
        assertThat(SemanaDeEleccion.esElegible(LocalDate.of(2026, 9, 27), JUEVES)).isTrue();
        assertThat(SemanaDeEleccion.esElegible(LocalDate.of(2026, 9, 28), JUEVES)).isFalse();
    }
}

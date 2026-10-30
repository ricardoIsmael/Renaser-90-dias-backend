package com.renaser.os.rocks.domain.model.cierre;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué semana y qué días se muestran en la ficha de cierres (spec 017, FR-006). 2026-10-28 es miércoles. */
class SemanaDeCierresTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 26);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 28);
    private static final LocalDate PRIMERA = LocalDate.of(2026, 10, 12);

    @Test
    @DisplayName("el lunes de cualquier dia de la semana, domingo incluido")
    void lunesDe() {
        assertThat(SemanaDeCierres.lunesDe(LUNES)).isEqualTo(LUNES);
        assertThat(SemanaDeCierres.lunesDe(MIERCOLES)).isEqualTo(LUNES);
        assertThat(SemanaDeCierres.lunesDe(LocalDate.of(2026, 11, 1))).isEqualTo(LUNES);
    }

    @Test
    @DisplayName("sin pedido, la semana en curso; con pedido valido, esa")
    void resolverValida() {
        assertThat(SemanaDeCierres.resolver(null, MIERCOLES, PRIMERA)).isEqualTo(LUNES);
        assertThat(SemanaDeCierres.resolver(LocalDate.of(2026, 10, 19), MIERCOLES, PRIMERA)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(SemanaDeCierres.resolver(PRIMERA, MIERCOLES, PRIMERA)).isEqualTo(PRIMERA);
    }

    @Test
    @DisplayName("un dia que no es lunes, una semana futura o anterior a la primera son pedidos mal hechos")
    void resolverInvalida() {
        assertThatThrownBy(() -> SemanaDeCierres.resolver(MIERCOLES, MIERCOLES, PRIMERA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanaDeCierres.resolver(LocalDate.of(2026, 11, 2), MIERCOLES, PRIMERA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemanaDeCierres.resolver(LocalDate.of(2026, 10, 5), MIERCOLES, PRIMERA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sin primera semana (todavia no eligio su Dia 1) no hay tope hacia atras")
    void sinTopeSinDiaUno() {
        assertThat(SemanaDeCierres.resolver(LocalDate.of(2026, 9, 7), MIERCOLES, null)).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    @DisplayName("la semana en curso llega hasta hoy: nunca un dia que viene")
    void diasHastaHoy() {
        assertThat(SemanaDeCierres.dias(LUNES, MIERCOLES, PRIMERA))
                .containsExactly(LUNES, LUNES.plusDays(1), MIERCOLES);
    }

    @Test
    @DisplayName("una semana pasada trae sus siete dias")
    void semanaPasadaCompleta() {
        List<LocalDate> dias = SemanaDeCierres.dias(LocalDate.of(2026, 10, 19), MIERCOLES, PRIMERA);
        assertThat(dias).hasSize(7).startsWith(LocalDate.of(2026, 10, 19)).endsWith(LocalDate.of(2026, 10, 25));
    }

    @Test
    @DisplayName("la semana del Dia 1 empieza en el Dia 1, no en el lunes")
    void primeraSemanaRecortada() {
        LocalDate diaUno = LocalDate.of(2026, 10, 14); // miercoles
        assertThat(SemanaDeCierres.dias(PRIMERA, LocalDate.of(2026, 10, 20), diaUno))
                .hasSize(5).startsWith(diaUno).endsWith(LocalDate.of(2026, 10, 18));
    }

    @Test
    @DisplayName("sin Dia 1 elegido no hay dias; con el Dia 1 en el futuro tampoco")
    void sinDias() {
        assertThat(SemanaDeCierres.dias(LUNES, MIERCOLES, null)).isEmpty();
        assertThat(SemanaDeCierres.dias(LUNES, MIERCOLES, LocalDate.of(2026, 11, 2))).isEmpty();
    }
}

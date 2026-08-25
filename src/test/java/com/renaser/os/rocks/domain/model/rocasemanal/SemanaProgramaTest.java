package com.renaser.os.rocks.domain.model.rocasemanal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SemanaProgramaTest {

    @Test
    @DisplayName("inicio en lunes: semana 1 dura la semana completa hasta el domingo")
    void inicioEnLunesSemanaCompleta() {
        LocalDate lunes = LocalDate.of(2026, 8, 24); // lunes
        assertThat(SemanaPrograma.primerDomingoDesde(lunes)).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, lunes)).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 8, 30))).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 8, 31))).isEqualTo(2);
    }

    @Test
    @DisplayName("inicio a mitad de semana: semana 1 es corta (flexible)")
    void inicioAMitadDeSemanaEsCorta() {
        LocalDate miercoles = LocalDate.of(2026, 8, 26); // miercoles
        assertThat(SemanaPrograma.primerDomingoDesde(miercoles)).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(SemanaPrograma.numeroSemanaParaFecha(miercoles, LocalDate.of(2026, 8, 30))).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(miercoles, LocalDate.of(2026, 8, 31))).isEqualTo(2);
    }

    @Test
    @DisplayName("inicio en domingo: el primer domingo es el mismo dia de inicio")
    void inicioEnDomingoEsElMismoDia() {
        LocalDate domingo = LocalDate.of(2026, 8, 23);
        assertThat(SemanaPrograma.primerDomingoDesde(domingo)).isEqualTo(domingo);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(domingo, domingo)).isEqualTo(1);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(domingo, LocalDate.of(2026, 8, 24))).isEqualTo(2);
    }

    @Test
    @DisplayName("semanas avanzan de 7 en 7 despues de la semana 1")
    void semanasAvanzanDeSieteEnSiete() {
        LocalDate lunes = LocalDate.of(2026, 8, 24);
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 9, 6))).isEqualTo(2); // domingo semana 2
        assertThat(SemanaPrograma.numeroSemanaParaFecha(lunes, LocalDate.of(2026, 9, 7))).isEqualTo(3); // lunes semana 3
    }
}

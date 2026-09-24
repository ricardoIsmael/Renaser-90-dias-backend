package com.renaser.os.rag.domain.model.agenda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgendaSemanalTest {

    private static final Set<DayOfWeek> LUNES_A_VIERNES = EnumSet.range(MONDAY, FRIDAY);

    @Test
    @DisplayName("guardar unos dias reemplaza solo esos dias y deja los demas como estaban")
    void reemplazaSoloEsosDias() {
        AgendaSemanal agenda = AgendaSemanal.vacia()
                .conDias(LUNES_A_VIERNES, "09:00-18:00")
                .conDias(Set.of(SATURDAY), "10:00-12:00")
                .conDias(Set.of(MONDAY), "08:00-12:00");

        assertThat(agenda.delDia(MONDAY).texto()).isEqualTo("08:00-12:00");
        assertThat(agenda.delDia(TUESDAY).texto()).isEqualTo("09:00-18:00");
        assertThat(agenda.delDia(SATURDAY).texto()).isEqualTo("10:00-12:00");
        assertThat(agenda.delDia(SUNDAY).estaLibre()).isTrue();
    }

    @Test
    @DisplayName("'ninguno' deja libres esos dias")
    void ningunoLosDejaLibres() {
        AgendaSemanal agenda = AgendaSemanal.vacia().conDias(LUNES_A_VIERNES, "09:00-18:00")
                .conDias(Set.of(MONDAY, TUESDAY), "ninguno");

        assertThat(agenda.delDia(MONDAY).estaLibre()).isTrue();
        assertThat(agenda.delDia(FRIDAY).texto()).isEqualTo("09:00-18:00");
    }

    @Test
    @DisplayName("un turno nocturno pasa su madrugada al dia siguiente, y el domingo a la madrugada del lunes")
    void turnoNocturno() {
        AgendaSemanal agenda = AgendaSemanal.vacia().conDias(Set.of(SUNDAY), "22:00-06:00");

        assertThat(agenda.delDia(SUNDAY).texto()).isEqualTo("22:00-24:00");
        assertThat(agenda.delDia(MONDAY).texto()).isEqualTo("00:00-06:00");
    }

    @Test
    @DisplayName("el texto lista los dias en orden, y sin nada dice que no hay horas guardadas")
    void texto() {
        assertThat(AgendaSemanal.vacia().texto()).isEqualTo("no tiene horas ocupadas guardadas");
        assertThat(AgendaSemanal.vacia().conDias(Set.of(FRIDAY, MONDAY), "09:00-13:00").texto())
                .isEqualTo("lunes 09:00-13:00; viernes 09:00-13:00");
    }

    @Test
    @DisplayName("tramos que no se entienden no cambian nada: IllegalArgumentException")
    void tramosInvalidos() {
        assertThatThrownBy(() -> AgendaSemanal.vacia().conDias(Set.of(MONDAY), "todo el dia"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PlanDeLaSemanaTest {

    private final HabitoId habito = HabitoId.of(UUID.randomUUID());
    private final SemanaCalendaria semana = SemanaCalendaria.desde(LocalDate.of(2026, 11, 2));
    /** Hoy es el miercoles 4. */
    private final LocalDate hoy = LocalDate.of(2026, 11, 4);

    private List<SemanaDelHabito.Dia> base() {
        return Stream.of(DayOfWeek.values())
                .map(d -> new SemanaDelHabito.Dia(d, LocalTime.of(7, 0), null, false, true,
                        RecordatorioDelDia.con(List.of(10)), false, CombinacionDeAlarma.COMO_HOY, false))
                .toList();
    }

    @Test
    void sinFilasEsElPlanDeSiempreYSoloMananaEnAdelanteSePuedeCambiar() {
        var dias = PlanDeLaSemana.resolver(semana, base(), List.of(), hoy);

        assertThat(dias).hasSize(7);
        assertThat(dias).allMatch(d -> !d.propioDeLaFecha());
        assertThat(dias.stream().filter(PlanDeLaSemana.DiaDeLaFecha::editable).map(PlanDeLaSemana.DiaDeLaFecha::fecha))
                .containsExactly(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 7),
                        LocalDate.of(2026, 11, 8));
    }

    @Test
    void laFechaGanaCampoPorCampo() {
        var fila = new FechaPropia(habito, LocalDate.of(2026, 11, 7), true, LocalTime.of(9, 0), null,
                RecordatorioDelDia.con(List.of(30, 0)), AjusteDeAlarma.de(CombinacionDeAlarma.de(false, true, false, false)));

        var sabado = PlanDeLaSemana.resolver(semana, base(), List.of(fila), hoy).get(5);

        assertThat(sabado.propioDeLaFecha()).isTrue();
        assertThat(sabado.dia().horaDisparo()).isEqualTo(LocalTime.of(9, 0));
        assertThat(sabado.dia().horaPropia()).isTrue();
        assertThat(sabado.dia().recordatorio().antelaciones()).containsExactly(30, 0);
        assertThat(sabado.dia().recordatorioPropio()).isTrue();
        assertThat(sabado.dia().alarma()).isEqualTo(CombinacionDeAlarma.de(false, true, false, false));
        assertThat(sabado.dia().alarmaPropia()).isTrue();
    }

    @Test
    void unaFilaDeSoloAvisoDejaLaHoraDeSiempre() {
        var fila = new FechaPropia(habito, LocalDate.of(2026, 11, 6), true, null, null, RecordatorioDelDia.sinAviso(), null);

        var viernes = PlanDeLaSemana.resolver(semana, base(), List.of(fila), hoy).get(4);

        assertThat(viernes.dia().horaDisparo()).isEqualTo(LocalTime.of(7, 0));
        assertThat(viernes.dia().horaPropia()).isFalse();
        assertThat(viernes.dia().recordatorio()).isEqualTo(RecordatorioDelDia.sinAviso());
    }

    @Test
    void unaFechaApagadaNoVaYLasDemasSiguenIgual() {
        var fila = FechaPropia.apagada(habito, LocalDate.of(2026, 11, 5));

        var dias = PlanDeLaSemana.resolver(semana, base(), List.of(fila), hoy);

        assertThat(dias.get(3).dia().activo()).isFalse();
        assertThat(dias.get(3).propioDeLaFecha()).isTrue();
        assertThat(dias.get(2).dia().activo()).isTrue();
    }

    @Test
    void unDiaApagadoEnElPlanDeSiempreSigueApagadoAunqueLaFechaTengaHora() {
        var apagadoLosMartes = base().stream()
                .map(d -> d.diaSemana() == DayOfWeek.TUESDAY
                        ? new SemanaDelHabito.Dia(d.diaSemana(), d.horaDisparo(), null, true, false, d.recordatorio(), false,
                        CombinacionDeAlarma.COMO_HOY, false)
                        : d)
                .toList();
        var fila = new FechaPropia(habito, LocalDate.of(2026, 11, 3), true, LocalTime.of(8, 0), null, null, null);

        var martes = PlanDeLaSemana.resolver(semana, apagadoLosMartes, List.of(fila), hoy).get(1);

        assertThat(martes.dia().activo()).isFalse();
    }

    @Test
    void unDiaApagadoNoAvisaConElAvisoDeLaFecha() {
        var fila = new FechaPropia(habito, LocalDate.of(2026, 11, 7), false, null, null, RecordatorioDelDia.con(List.of(5)), null);

        var sabado = PlanDeLaSemana.resolver(semana, base(), List.of(fila), hoy).get(5);

        assertThat(sabado.dia().recordatorio().antelaciones()).containsExactly(10);
    }
}

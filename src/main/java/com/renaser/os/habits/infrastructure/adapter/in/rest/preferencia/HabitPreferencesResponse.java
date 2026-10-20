package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CambioProgramado;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.HorarioDeHabito;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;
import com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia.HabitPreferenceResponse.ScheduleEditQuotaResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Salida de {@code GET /api/v1/habit-preferences}. Nombres de campo en ingles y {@code scheduleEdits}
 * con la misma forma que devuelve el PATCH ({@link HabitPreferenceResponse}) — el cliente lee la cuota
 * igual venga de donde venga.
 */
public record HabitPreferencesResponse(List<HabitPreferenceItemResponse> habits,
                                        ScheduleEditQuotaResponse scheduleEdits, LocalDate date) {

    public static HabitPreferencesResponse from(ResumenPreferenciasHorario resumen, LocalDate date) {
        var cuota = resumen.cuota();
        return new HabitPreferencesResponse(
                resumen.habitos().stream().map(HabitPreferenceItemResponse::from).toList(),
                new ScheduleEditQuotaResponse(cuota.cambiosUsados(), cuota.cambiosRestantes(),
                        cuota.cambiosLimite(), cuota.periodo()), date);
    }

    /**
     * {@code triggerTime}/{@code limitTime}: lo que rige en la fecha consultada. {@code customized}: si el horario sale de
     * una preferencia propia o del catalogo. {@code pendingChange}: {@code null} si no hay nada programado.
     * {@code reminderMinutesList} (D-217, aditivo): todas las antelaciones, de la más temprana a la más
     * tardía; {@code null} = no se conocen (fila anterior a V81). {@code reminderMinutesBefore} sigue
     * siendo la más temprana, para el APK de producción.
     *
     * <p>{@code weekdays} (spec 008, D-282, aditivo): los siete días ya resueltos. Los {@code reminder…} del
     * ítem son el aviso GENERAL del hábito, no el del día de la semana consultado; el de cada día está en su
     * entrada de {@code weekdays}.
     */
    public record HabitPreferenceItemResponse(UUID habitId, String title, LocalTime triggerTime, LocalTime limitTime,
                                               boolean customized, boolean reminderEnabled,
                                               Integer reminderMinutesBefore,
                                               PendingScheduleChangeResponse pendingChange,
                                               List<Integer> reminderMinutesList,
                                               List<HabitWeekdayResponse> weekdays) {

        static HabitPreferenceItemResponse from(HorarioDeHabito h) {
            return new HabitPreferenceItemResponse(h.habitoId().value(), h.titulo(), h.horaDisparo(), h.horaLimite(),
                    h.personalizado(), h.recordatorioActivo(), h.minutosRecordatorio(),
                    PendingScheduleChangeResponse.from(h.cambioProgramado()), h.antelacionesRecordatorio(),
                    h.diasDeLaSemana().stream().map(HabitWeekdayResponse::from).toList());
        }
    }

    /**
     * Un día de la semana de un hábito, ya resuelto. {@code customTime}: el día tiene hora propia (o está
     * apagado); {@code customReminder}: el aviso es propio y no el general. Sin {@code limitTime}: la lista
     * solo sirve para armar las alarmas.
     */
    public record HabitWeekdayResponse(String weekday, LocalTime triggerTime, boolean active,
                                       boolean reminderEnabled, List<Integer> reminderMinutesList,
                                       boolean customTime, boolean customReminder) {

        static HabitWeekdayResponse from(SemanaDelHabito.Dia d) {
            return new HabitWeekdayResponse(d.diaSemana().name(), d.horaDisparo(), d.activo(),
                    d.recordatorio().activo(), d.recordatorio().antelaciones(), d.horaPropia(),
                    d.recordatorioPropio());
        }
    }

    /** {@code effectiveDate}: mismo dato que el PATCH devuelve como {@code deferredEffectiveDate}. */
    public record PendingScheduleChangeResponse(LocalTime triggerTime, LocalTime limitTime, LocalDate effectiveDate) {

        static PendingScheduleChangeResponse from(CambioProgramado cambio) {
            return cambio == null ? null
                    : new PendingScheduleChangeResponse(cambio.horaDisparo(), cambio.horaLimite(),
                            cambio.fechaEfectiva());
        }
    }
}

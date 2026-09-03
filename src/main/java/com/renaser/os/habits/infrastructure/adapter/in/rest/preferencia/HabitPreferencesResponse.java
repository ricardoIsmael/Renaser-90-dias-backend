package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CambioProgramado;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.HorarioDeHabito;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario;
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
                                        ScheduleEditQuotaResponse scheduleEdits) {

    public static HabitPreferencesResponse from(ResumenPreferenciasHorario resumen) {
        var cuota = resumen.cuota();
        return new HabitPreferencesResponse(
                resumen.habitos().stream().map(HabitPreferenceItemResponse::from).toList(),
                new ScheduleEditQuotaResponse(cuota.cambiosUsados(), cuota.cambiosRestantes(),
                        cuota.cambiosLimite(), cuota.periodo()));
    }

    /**
     * {@code triggerTime}/{@code limitTime}: lo que rige HOY. {@code customized}: si el horario sale de
     * una preferencia propia o del catalogo. {@code pendingChange}: {@code null} si no hay nada programado.
     */
    public record HabitPreferenceItemResponse(UUID habitId, String title, LocalTime triggerTime, LocalTime limitTime,
                                               boolean customized, PendingScheduleChangeResponse pendingChange) {

        static HabitPreferenceItemResponse from(HorarioDeHabito h) {
            return new HabitPreferenceItemResponse(h.habitoId().value(), h.titulo(), h.horaDisparo(), h.horaLimite(),
                    h.personalizado(), PendingScheduleChangeResponse.from(h.cambioProgramado()));
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

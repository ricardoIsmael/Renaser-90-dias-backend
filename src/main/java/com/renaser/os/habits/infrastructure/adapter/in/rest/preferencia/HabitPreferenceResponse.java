package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record HabitPreferenceResponse(UUID habitId, LocalTime triggerTime, LocalTime limitTime, boolean deferred,
                                       LocalDate deferredEffectiveDate, ScheduleEditQuotaResponse scheduleEdits) {

    public static HabitPreferenceResponse from(ResultadoEdicionPreferencia r) {
        return new HabitPreferenceResponse(r.habitoId().value(), r.horaDisparo(), r.horaLimite(), r.diferido(),
                r.fechaEfectivaDiferido(),
                new ScheduleEditQuotaResponse(r.cambiosUsados(), r.cambiosRestantes(), r.cambiosLimite(),
                        r.periodo()));
    }

    public record ScheduleEditQuotaResponse(int used, int remaining, int limit, String period) {
    }
}

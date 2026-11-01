package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Cuerpo de {@code PUT /api/v1/habit-preferences/{habitId}/week} (spec 028, D-296): SOLO LO TOCADO de varias fechas de una semana. Un campo
 * ausente no se toca; {@code reset} devuelve la fecha al plan de siempre. Contrato en
 * {@code specs/028-planificacion-semana-y-mes/contracts/habit-preferences-week.md}.
 *
 * <p>{@code weekStart} es obligatorio ({@code Boolean} y no {@code boolean} en lo que apaga algo, por la misma razon que el aviso por dia: omitir
 * un campo no puede significar «apagalo»). Los valores los valida el dominio: un 400 si no valen.
 */
public record WeekChangesRequest(@NotNull LocalDate weekStart, @NotEmpty List<@Valid @NotNull WeekDayChange> days) {

    public record WeekDayChange(@NotNull LocalDate date, Boolean reset, Boolean active, LocalTime triggerTime, LocalTime limitTime,
                                Boolean reminderEnabled, List<Integer> reminderMinutesList, @Valid AlarmCombinationRequest alarm) {

        /** @throws IllegalArgumentException si el cambio se contradice o un valor no vale (400) */
        CambioDeFecha toChange() {
            if (Boolean.TRUE.equals(reset)) {
                if (active != null || triggerTime != null || limitTime != null || reminderEnabled != null
                        || reminderMinutesList != null || alarm != null) {
                    throw new IllegalArgumentException("Un dia que vuelve al plan de siempre no lleva otros cambios: " + date);
                }
                return CambioDeFecha.restablecer(date);
            }
            if (limitTime != null && triggerTime == null) {
                throw new IllegalArgumentException("limitTime solo se manda junto con triggerTime");
            }
            if (reminderEnabled == null && reminderMinutesList != null) {
                throw new IllegalArgumentException("reminderMinutesList necesita reminderEnabled");
            }
            return new CambioDeFecha(date, false, active, triggerTime, limitTime, aviso(), alarm == null ? null : alarm.toCombination());
        }

        private RecordatorioDelDia aviso() {
            if (reminderEnabled == null) {
                return null;
            }
            return reminderEnabled ? RecordatorioDelDia.con(reminderMinutesList) : RecordatorioDelDia.sinAviso();
        }
    }

    List<CambioDeFecha> toChanges() {
        return days.stream().map(WeekDayChange::toChange).toList();
    }
}

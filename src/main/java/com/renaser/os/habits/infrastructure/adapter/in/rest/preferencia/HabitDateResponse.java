package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.domain.model.preferencia.PlanDeLaSemana.DiaDeLaFecha;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * UNA fecha de un habito, ya resuelta (spec 028, D-296): el plan de siempre con lo «solo esta semana» encima. Es la forma de un dia de
 * {@code weekdays} con su fecha. {@code customTime}, {@code customReminder} y {@code customAlarm} valen {@code true} si el valor es propio
 * del dia de la semana O de la fecha; {@code customDate}, si la fecha tiene fila propia; {@code editable}, si todavia se puede cambiar
 * (es posterior a hoy). Contrato en {@code specs/028-planificacion-semana-y-mes/contracts/habit-preferences-week.md}.
 */
public record HabitDateResponse(LocalDate date, String weekday, LocalTime triggerTime, LocalTime limitTime, boolean active,
                                boolean reminderEnabled, List<Integer> reminderMinutesList, boolean customTime,
                                boolean customReminder, AlarmCombinationResponse alarm, boolean customAlarm,
                                boolean customDate, boolean editable) {

    static HabitDateResponse from(DiaDeLaFecha f) {
        SemanaDelHabito.Dia d = f.dia();
        return new HabitDateResponse(f.fecha(), d.diaSemana().name(), d.horaDisparo(), d.horaLimite(), d.activo(),
                d.recordatorio().activo(), d.recordatorio().antelaciones(), d.horaPropia(), d.recordatorioPropio(),
                AlarmCombinationResponse.from(d.alarma()), d.alarmaPropia(), f.propioDeLaFecha(), f.editable());
    }
}

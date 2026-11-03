package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.DiaDeLaOferta;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.HabitoDeLaOferta;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.HabitoExcluido;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.OfertaDeRepeticion;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.ResultadoDeRepeticion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Salidas de repetir la semana (spec 028, D-296). Contrato en
 * {@code specs/028-planificacion-semana-y-mes/contracts/habit-preferences-week-repeat.md}.
 */
public final class WeekRepeatResponses {

    private WeekRepeatResponses() {
    }

    public record Excluded(UUID habitId, String title, String reason) {
        static Excluded from(HabitoExcluido e) {
            return new Excluded(e.habitoId().value(), e.titulo(), e.motivo());
        }
    }

    /**
     * Lo que quedaria en una fecha del destino. Los campos propios vienen solo si lo son; una fecha que vuelve al plan de siempre viene sin
     * ninguno ({@code replaces = true}).
     */
    public record OfferDay(LocalDate date, String weekday, boolean active, LocalTime triggerTime, Boolean reminderEnabled,
                           List<Integer> reminderMinutesList, boolean customTime, boolean customReminder,
                           AlarmCombinationResponse alarm, boolean replaces) {
        static OfferDay from(DiaDeLaOferta d) {
            return new OfferDay(d.fecha(), d.diaSemana().name(), d.activo(), d.horaDisparo(), d.recordatorioActivo(),
                    d.antelaciones(), d.horaDisparo() != null, d.recordatorioActivo() != null,
                    d.alarma() == null ? null : AlarmCombinationResponse.from(d.alarma()), d.reemplaza());
        }
    }

    public record OfferHabit(UUID habitId, String title, List<OfferDay> days) {
        static OfferHabit from(HabitoDeLaOferta h) {
            return new OfferHabit(h.habitoId().value(), h.titulo(), h.dias().stream().map(OfferDay::from).toList());
        }
    }

    public record Offer(LocalDate sourceWeekStart, LocalDate targetWeekStart, String fingerprint, boolean replacesOwnChanges,
                        List<OfferHabit> habits, List<Excluded> excluded) {
        static Offer from(OfertaDeRepeticion o) {
            return new Offer(o.modelo().lunes(), o.destino().lunes(), o.huella(), o.reemplazaLoPropio(),
                    o.habitos().stream().map(OfferHabit::from).toList(), o.excluidos().stream().map(Excluded::from).toList());
        }
    }

    public record Result(LocalDate targetWeekStart, int copiedDays, int removedDays, int habits, List<Excluded> excluded) {
        static Result from(ResultadoDeRepeticion r) {
            return new Result(r.destino().lunes(), r.diasCopiados(), r.diasQueVuelvenAlPlan(), r.habitos(),
                    r.excluidos().stream().map(Excluded::from).toList());
        }
    }

    public record Undone(int removedDays) {
    }
}

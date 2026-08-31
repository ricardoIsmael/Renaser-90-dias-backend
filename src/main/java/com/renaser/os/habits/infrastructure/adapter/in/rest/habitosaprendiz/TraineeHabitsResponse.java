package com.renaser.os.habits.infrastructure.adapter.in.rest.habitosaprendiz;

import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.CuotaCambiosHorario;
import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.VistaHabitosDeAprendiz;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/v1/admin/trainees/{traineeId}/habits}. Envuelve la lista en
 * un objeto (y no la devuelve pelada) porque el panel necesita, ademas de los habitos, el
 * contexto contra el que se resolvieron: dia de programa, fecha local del aprendiz y cuota
 * de reacomodos ya gastada esta semana.
 *
 * <p><b>Sin paginacion, a proposito</b> — el tope de {@code habits} es "los habitos activos
 * de UNA persona" (catalogo de sistema activo + sus personales), no crece con el uso; ver
 * el javadoc de {@code LeerHabitosPersonalizadosPort}.
 */
public record TraineeHabitsResponse(UUID traineeId, int programDay, LocalDate localDate, String timeZone,
                                     ScheduleEditQuotaResponse scheduleEdits, List<TraineeHabitResponse> habits) {

    public static TraineeHabitsResponse from(VistaHabitosDeAprendiz vista) {
        return new TraineeHabitsResponse(vista.aprendizId().value(), vista.diaPrograma(), vista.fechaLocal(),
                vista.zonaHoraria(), ScheduleEditQuotaResponse.from(vista.cuota()),
                vista.habitos().stream().map(TraineeHabitResponse::from).toList());
    }

    /** Mismos nombres y literales de {@code period} que el autoservicio ya devuelve al aprendiz. */
    public record ScheduleEditQuotaResponse(int used, int remaining, int limit, String period) {

        static ScheduleEditQuotaResponse from(CuotaCambiosHorario cuota) {
            return new ScheduleEditQuotaResponse(cuota.usados(), cuota.restantes(), cuota.limite(), cuota.periodo());
        }
    }
}

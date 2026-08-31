package com.renaser.os.habits.infrastructure.adapter.in.rest.habitosaprendiz;

import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.CambioHorarioProgramado;
import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.DesbloqueoDeAprendiz;
import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.HabitoDeAprendiz;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitCategoryDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitTypeDto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Un habito del aprendiz, tal como lo ve el panel. Proyeccion explicita escrita a mano
 * (CLAUDE.MD §5.4.1/§5.4.5): nunca la entidad serializada, y nunca un mapeo automatico que
 * filtre a la respuesta cualquier campo que se agregue manana al modelo.
 *
 * <p>{@code habitType}/{@code category} reusan los enums que ya publica el admin de
 * catalogo ({@code /api/v1/admin/habits}), para que el mismo habito se llame igual en las
 * dos pantallas del panel.
 *
 * <p>{@code triggerTime}/{@code limitTime} son el horario VIGENTE (preferencia del aprendiz
 * pisando al catalogo); {@code customSchedule} dice si esa hora la puso la persona o viene
 * del catalogo, que es la pregunta que el operador hace de verdad.
 */
public record TraineeHabitResponse(UUID habitId, String catalogTitle, String personalTitle, boolean isPersonal,
                                    HabitTypeDto habitType, HabitCategoryDto category, LocalTime triggerTime,
                                    LocalTime limitTime, boolean customSchedule, Boolean reminderEnabled,
                                    Integer reminderMinutesBefore, PendingScheduleChangeResponse pendingScheduleChange,
                                    HabitUnlockResponse unlock, boolean weeklyDayChoice, LocalDate chosenWeeklyDate) {

    public static TraineeHabitResponse from(HabitoDeAprendiz habito) {
        return new TraineeHabitResponse(habito.habitoId().value(), habito.tituloCatalogo(), habito.tituloPersonal(),
                habito.esPersonal(), HabitTypeDto.from(habito.tipo()),
                HabitCategoryDto.fromClave(habito.categoriaClave()), habito.horaDisparo(), habito.horaLimite(),
                habito.horarioPersonalizado(), habito.recordatorioActivo(), habito.minutosRecordatorio(),
                PendingScheduleChangeResponse.from(habito.cambioPendiente()),
                HabitUnlockResponse.from(habito.desbloqueo()), habito.eleccionDiaSemanal(),
                habito.diaSemanalElegido());
    }

    /** {@code null} si no hay cambio programado. */
    public record PendingScheduleChangeResponse(LocalTime triggerTime, LocalTime limitTime, LocalDate effectiveDate) {

        static PendingScheduleChangeResponse from(CambioHorarioProgramado cambio) {
            return cambio == null ? null
                    : new PendingScheduleChangeResponse(cambio.horaDisparo(), cambio.horaLimite(),
                            cambio.fechaEfectiva());
        }
    }

    /** {@code chosenByTrainee=false} = lo puso el relleno automatico ({@code elegido_en} NULL). */
    public record HabitUnlockResponse(int programDay, boolean chosenByTrainee) {

        static HabitUnlockResponse from(DesbloqueoDeAprendiz desbloqueo) {
            return desbloqueo == null ? null
                    : new HabitUnlockResponse(desbloqueo.diaDesbloqueo(), desbloqueo.elegidoPorLaPersona());
        }
    }
}

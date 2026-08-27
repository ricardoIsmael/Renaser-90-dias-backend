package com.renaser.os.habits.application.ports.in.horarioadmin;

import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * Edicion parcial de un horario (PATCH-like: cada campo que llega null en el request
 * mantiene el valor actual — resuelto en el servicio, no aca, porque {@code diaFin}/
 * {@code horaDisparo}/{@code horaLimite} nulos son valores de negocio validos por si
 * mismos y no se puede distinguir "no lo mande" de "lo mande en null" en un record).
 * Solo ADMIN/ALCHEMIST.
 */
public interface ActualizarHorarioHabitoUseCase {

    HorarioHabito actualizar(ActualizarHorarioHabitoCommand command);

    record ActualizarHorarioHabitoCommand(@NotNull UserId actorId, @NotNull HorarioHabitoId horarioId,
                                           Integer diaInicio, Integer diaFin, TipoDia tipoDia,
                                           LocalTime horaDisparo, LocalTime horaLimite, boolean limpiarDiaFin,
                                           boolean limpiarHoraDisparo, boolean limpiarHoraLimite) {
        public ActualizarHorarioHabitoCommand {
            SelfValidating.validateConstructorArgs(ActualizarHorarioHabitoCommand.class, actorId, horarioId,
                    diaInicio, diaFin, tipoDia, horaDisparo, horaLimite, limpiarDiaFin, limpiarHoraDisparo,
                    limpiarHoraLimite);
        }
    }
}

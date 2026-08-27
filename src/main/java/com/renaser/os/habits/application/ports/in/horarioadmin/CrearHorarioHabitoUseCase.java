package com.renaser.os.habits.application.ports.in.horarioadmin;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/** Alta de un horario de catalogo para un habito. Solo ADMIN/ALCHEMIST. */
public interface CrearHorarioHabitoUseCase {

    HorarioHabito crear(CrearHorarioHabitoCommand command);

    record CrearHorarioHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, int diaInicio,
                                      Integer diaFin, @NotNull TipoDia tipoDia, LocalTime horaDisparo,
                                      LocalTime horaLimite) {
        public CrearHorarioHabitoCommand {
            SelfValidating.validateConstructorArgs(CrearHorarioHabitoCommand.class, actorId, habitoId, diaInicio,
                    diaFin, tipoDia, horaDisparo, horaLimite);
        }
    }
}

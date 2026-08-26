package com.renaser.os.habits.application.ports.in.renombre;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.renombre.RenombreHabito;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Hueco #12 — el aprendiz reemplaza el rotulo de JUGO VERDE o AGUA TIBIA CON LIMON
 * (renameableKeys.ts). Solo hasta el dia 0 de programa — despues el habito ya genero
 * tracks con su nombre.
 */
public interface RenombrarHabitoUseCase {

    RenombreHabito renombrar(RenombrarHabitoCommand command);

    record RenombrarHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                   @NotBlank @Size(max = 60) String tituloPersonal,
                                   @NotBlank @Size(max = 200) String motivo) {
        public RenombrarHabitoCommand {
            SelfValidating.validateConstructorArgs(RenombrarHabitoCommand.class, actorId, habitoId, tituloPersonal,
                    motivo);
        }
    }
}

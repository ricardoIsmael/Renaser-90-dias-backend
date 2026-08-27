package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/** Baja/alta logica del catalogo (toggle isActive del panel). Solo ADMIN/ALCHEMIST. */
public interface CambiarActivoHabitoUseCase {

    Habito cambiarActivo(CambiarActivoHabitoCommand command);

    record CambiarActivoHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId, boolean activo) {
        public CambiarActivoHabitoCommand {
            SelfValidating.validateConstructorArgs(CambiarActivoHabitoCommand.class, actorId, habitoId, activo);
        }
    }
}

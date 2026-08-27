package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Edicion de un habito de catalogo. Solo ADMIN/ALCHEMIST. NO acepta {@code titulo} ni
 * {@code tipo} — ver el javadoc de {@code Habito.actualizarDetalles} para el porque de
 * cada uno; el DTO de entrada REST tampoco los expone, asi el compilador (no un `if` en
 * runtime) es el que impide reintroducirlos por descuido (mismo criterio que el blindaje
 * de {@code role} en altas publicas, CLAUDE.MD §5.3.3).
 */
public interface ActualizarHabitoUseCase {

    Habito actualizar(ActualizarHabitoCommand command);

    record ActualizarHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId,
                                    @NotNull DetallesHabito detalles) {
        public ActualizarHabitoCommand {
            SelfValidating.validateConstructorArgs(ActualizarHabitoCommand.class, actorId, habitoId, detalles);
        }
    }
}

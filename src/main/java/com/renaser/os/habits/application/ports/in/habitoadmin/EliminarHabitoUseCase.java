package com.renaser.os.habits.application.ports.in.habitoadmin;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Borrado fisico de un habito de catalogo. Solo ADMIN/ALCHEMIST. Sin historial
 * ({@code registros_habito}) sale; con historial, Postgres frena el DELETE (FK RESTRICT,
 * P-02) y el request vuelve 409 — ver {@code SaveHabitoPort.eliminar}.
 */
public interface EliminarHabitoUseCase {

    void eliminar(EliminarHabitoCommand command);

    record EliminarHabitoCommand(@NotNull UserId actorId, @NotNull HabitoId habitoId) {
        public EliminarHabitoCommand {
            SelfValidating.validateConstructorArgs(EliminarHabitoCommand.class, actorId, habitoId);
        }
    }
}

package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Asigna una celula a un participante (gap #25 de docs/PLAN_INTEGRACION_FRONTEND.md §5).
 * Administrativo (ADMIN/ALCHEMIST), mismo criterio que {@link AssignMentorToTraineeUseCase}.
 *
 * <p>NO valida que {@code celulaId} exista: esa validacion es responsabilidad de
 * `community` (dueño del agregado {@code Celula}), que debe confirmar la celula antes de
 * invocar este puerto. `users` solo es dueño de la columna
 * {@code participantes_programa.celula_id}.
 */
public interface AssignTraineeCellUseCase {

    void assign(AssignTraineeCellCommand command);

    record AssignTraineeCellCommand(@NotNull UserId actorId, @NotNull UserId traineeId, @NotNull UUID celulaId) {

        public AssignTraineeCellCommand {
            SelfValidating.validateConstructorArgs(AssignTraineeCellCommand.class, actorId, traineeId, celulaId);
        }
    }
}

package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Asigna un aprendiz a una célula (gap #25, docs/PLAN_INTEGRACION_FRONTEND.md §5).
 * {@code celula_id} vive en {@code participantes_programa} (tabla de `users`) — este caso
 * de uso valida que la célula exista ACÁ (dueño del agregado {@code Celula}) y delega la
 * escritura en {@code users.api.AsignacionCelulaPort}.
 */
public interface AsignarAprendizCelulaUseCase {

    void asignar(AsignarAprendizCelulaCommand command);

    record AsignarAprendizCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId,
                                         @NotNull UserId traineeId) {

        public AsignarAprendizCelulaCommand {
            SelfValidating.validateConstructorArgs(AsignarAprendizCelulaCommand.class, actorId, celulaId, traineeId);
        }
    }
}

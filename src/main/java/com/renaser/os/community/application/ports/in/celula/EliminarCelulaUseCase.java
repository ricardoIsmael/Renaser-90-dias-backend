package com.renaser.os.community.application.ports.in.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface EliminarCelulaUseCase {

    /** Borra la celula. Los aprendices que la tenian asignada quedan sin celula
     * (`participantes_programa.celula_id` -> null por ON DELETE SET NULL, no destruye
     * cuentas — community/service.ts:467-491). */
    void eliminar(EliminarCelulaCommand command);

    record EliminarCelulaCommand(@NotNull UserId actorId, @NotNull CelulaId celulaId) {

        public EliminarCelulaCommand {
            SelfValidating.validateConstructorArgs(EliminarCelulaCommand.class, actorId, celulaId);
        }
    }
}

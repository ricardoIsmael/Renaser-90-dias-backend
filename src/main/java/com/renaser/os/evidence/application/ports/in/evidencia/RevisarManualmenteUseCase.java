package com.renaser.os.evidence.application.ports.in.evidencia;

import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/** Un admin resuelve (aprueba/rechaza) una evidencia caída en REVISION_MANUAL — solo ADMIN/ALCHEMIST. */
public interface RevisarManualmenteUseCase {

    Evidencia revisar(RevisarManualmenteCommand command);

    record RevisarManualmenteCommand(@NotNull UserId actorId, @NotNull EvidenciaId evidenciaId, boolean aprobar,
                                      String notas) {

        public RevisarManualmenteCommand {
            SelfValidating.validateConstructorArgs(RevisarManualmenteCommand.class, actorId, evidenciaId, aprobar,
                    notas);
        }
    }
}

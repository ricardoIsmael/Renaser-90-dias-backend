package com.renaser.os.evidence.application.ports.in.evidencia;

import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Un admin anula el veredicto (VALIDA/RECHAZADA) de una evidencia ya resuelta — solo ADMIN/ALCHEMIST. */
public interface AnularVeredictoUseCase {

    Evidencia anular(AnularVeredictoCommand command);

    record AnularVeredictoCommand(@NotNull UserId actorId, @NotNull EvidenciaId evidenciaId,
                                   @NotBlank String notas) {

        public AnularVeredictoCommand {
            SelfValidating.validateConstructorArgs(AnularVeredictoCommand.class, actorId, evidenciaId, notas);
        }
    }
}

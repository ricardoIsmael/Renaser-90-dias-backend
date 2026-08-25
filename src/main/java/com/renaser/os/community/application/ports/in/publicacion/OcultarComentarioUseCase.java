package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface OcultarComentarioUseCase {

    /** El autor puede ocultar el propio; ADMIN/ALCHEMIST pueden ocultar cualquiera como
     * moderacion (wall/service.ts:372-387). */
    Resultado ocultar(OcultarComentarioCommand command);

    record OcultarComentarioCommand(@NotNull UserId actorId, @NotNull ComentarioId comentarioId) {

        public OcultarComentarioCommand {
            SelfValidating.validateConstructorArgs(OcultarComentarioCommand.class, actorId, comentarioId);
        }
    }

    record Resultado(int cantidadComentarios) {
    }
}

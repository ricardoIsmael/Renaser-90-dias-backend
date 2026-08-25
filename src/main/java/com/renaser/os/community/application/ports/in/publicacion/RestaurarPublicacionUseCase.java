package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface RestaurarPublicacionUseCase {

    /** Solo moderacion — el autor no puede re-publicar por si solo algo que el equipo
     * decidio ocultar (wall/service.ts:182-194). */
    void restaurar(RestaurarPublicacionCommand command);

    record RestaurarPublicacionCommand(@NotNull UserId actorId, @NotNull PublicacionId publicacionId) {

        public RestaurarPublicacionCommand {
            SelfValidating.validateConstructorArgs(RestaurarPublicacionCommand.class, actorId, publicacionId);
        }
    }
}

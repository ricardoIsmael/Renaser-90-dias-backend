package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface OcultarPublicacionUseCase {

    /** El autor puede ocultar la suya; ADMIN/ALCHEMIST pueden ocultar cualquiera como
     * moderacion (wall/service.ts:162-180). */
    void ocultar(OcultarPublicacionCommand command);

    record OcultarPublicacionCommand(@NotNull UserId actorId, @NotNull PublicacionId publicacionId) {

        public OcultarPublicacionCommand {
            SelfValidating.validateConstructorArgs(OcultarPublicacionCommand.class, actorId, publicacionId);
        }
    }
}

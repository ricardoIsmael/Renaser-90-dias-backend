package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface EliminarPublicacionUseCase {

    /** Borrado fisico, solo desde la cola de moderacion — nunca hay un boton irreversible
     * sobre el feed publico (wall/service.ts:196-208). */
    void eliminarPermanente(EliminarPublicacionCommand command);

    record EliminarPublicacionCommand(@NotNull UserId actorId, @NotNull PublicacionId publicacionId) {

        public EliminarPublicacionCommand {
            SelfValidating.validateConstructorArgs(EliminarPublicacionCommand.class, actorId, publicacionId);
        }
    }
}

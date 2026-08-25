package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.PublicacionVista;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.ArchivoEntrada;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface EditarPublicacionUseCase {

    /** Solo el propio autor edita — sin bypass de moderacion (wall/service.ts:137-138). La
     * categoria no se toca al editar. */
    PublicacionVista editar(EditarPublicacionCommand command);

    record EditarPublicacionCommand(@NotNull UserId actorId, @NotNull PublicacionId publicacionId,
                                     @NotNull String texto, @NotEmpty List<ArchivoEntrada> media) {

        public EditarPublicacionCommand {
            SelfValidating.validateConstructorArgs(EditarPublicacionCommand.class, actorId, publicacionId, texto,
                    media);
        }
    }
}

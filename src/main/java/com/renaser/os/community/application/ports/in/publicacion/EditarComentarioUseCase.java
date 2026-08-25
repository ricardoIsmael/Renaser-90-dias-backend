package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase.ComentarioVista;
import com.renaser.os.community.domain.model.publicacion.ComentarioId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface EditarComentarioUseCase {

    /** Solo el autor — mismo criterio que editar una publicacion: reescribir lo que otro
     * dijo no es moderacion (wall/service.ts:389-391). */
    ComentarioVista editar(EditarComentarioCommand command);

    record EditarComentarioCommand(@NotNull UserId actorId, @NotNull ComentarioId comentarioId,
                                    @NotBlank String texto) {

        public EditarComentarioCommand {
            SelfValidating.validateConstructorArgs(EditarComentarioCommand.class, actorId, comentarioId, texto);
        }
    }
}

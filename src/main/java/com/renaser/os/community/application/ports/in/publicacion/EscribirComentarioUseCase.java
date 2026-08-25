package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase.ComentarioVista;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface EscribirComentarioUseCase {

    Resultado escribir(EscribirComentarioCommand command);

    record EscribirComentarioCommand(@NotNull UserId autorId, @NotNull PublicacionId publicacionId,
                                      @NotBlank String texto) {

        public EscribirComentarioCommand {
            SelfValidating.validateConstructorArgs(EscribirComentarioCommand.class, autorId, publicacionId, texto);
        }
    }

    /** Devuelve el contador junto al comentario para que la tarjeta del feed quede exacta
     * sin recargar (wall/service.ts:359-362). */
    record Resultado(ComentarioVista comentario, int cantidadComentarios) {
    }
}

package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ConsultarComentariosUseCase {

    /** Spec 024: los comentarios se leen con el actor, porque la publicación tiene que ser de una formación que ve (si no, es como si no existiera). */
    PaginaComentarios pagina(UserId actorId, PublicacionId publicacionId, Instant cursor);

    record PaginaComentarios(List<ComentarioVista> comentarios, Instant siguienteCursor, int total) {
    }

    /** Spec 046: {@code autorNombre} es el del Muro (su sobrenombre); {@code autorNombreReal}, solo para el equipo y si tiene sobrenombre. */
    record ComentarioVista(Comentario comentario, String autorNombre, String autorAvatarUrl, String autorNombreReal) {

        public ComentarioVista(Comentario comentario, String autorNombre, String autorAvatarUrl) {
            this(comentario, autorNombre, autorAvatarUrl, null);
        }
    }
}

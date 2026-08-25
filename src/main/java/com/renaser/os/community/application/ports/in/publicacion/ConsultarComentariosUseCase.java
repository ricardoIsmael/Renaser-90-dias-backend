package com.renaser.os.community.application.ports.in.publicacion;

import com.renaser.os.community.domain.model.publicacion.Comentario;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;

import java.time.Instant;
import java.util.List;

public interface ConsultarComentariosUseCase {

    PaginaComentarios pagina(PublicacionId publicacionId, Instant cursor);

    record PaginaComentarios(List<ComentarioVista> comentarios, Instant siguienteCursor, int total) {
    }

    record ComentarioVista(Comentario comentario, String autorNombre, String autorAvatarUrl) {
    }
}

package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase.ComentarioVista;
import com.renaser.os.community.domain.model.publicacion.Comentario;

public record WallCommentResponse(String id, String postId, String authorId, String authorName,
                                   String authorAvatarUrl, String text, String createdAt) {

    public static WallCommentResponse from(ComentarioVista vista) {
        Comentario c = vista.comentario();
        return new WallCommentResponse(c.id().toString(), c.publicacionId().toString(), c.autorId().toString(),
                vista.autorNombre(), vista.autorAvatarUrl(), c.texto(), c.creadoEn().toString());
    }
}

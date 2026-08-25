package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarComentariosUseCase.PaginaComentarios;

import java.util.List;

public record WallCommentsPageResponse(List<WallCommentResponse> comments, String nextCursor, int total) {

    public static WallCommentsPageResponse from(PaginaComentarios pagina) {
        return new WallCommentsPageResponse(pagina.comentarios().stream().map(WallCommentResponse::from).toList(),
                pagina.siguienteCursor() != null ? pagina.siguienteCursor().toString() : null, pagina.total());
    }
}

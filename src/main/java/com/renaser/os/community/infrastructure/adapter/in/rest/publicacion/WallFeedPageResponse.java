package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.PaginaPublicaciones;

import java.util.List;

public record WallFeedPageResponse(List<WallPostResponse> posts, String nextCursor) {

    public static WallFeedPageResponse from(PaginaPublicaciones pagina) {
        return new WallFeedPageResponse(pagina.publicaciones().stream().map(WallPostResponse::from).toList(),
                pagina.siguienteCursor() != null ? pagina.siguienteCursor().toString() : null);
    }
}

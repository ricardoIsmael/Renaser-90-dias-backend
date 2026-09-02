package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase.ReaccionVista;

import java.util.List;

/**
 * {@code GET /api/v1/wall/{id}/reactions}. Sin conteos ni paginacion a proposito: el modal
 * "Reacciones del post" del diseño RN no pagina (una publicacion con muchas reacciones sigue
 * siendo una lista chica frente al feed), y los contadores de las tres pestañas (TODOS/LIKES/
 * DISLIKES) salen de contar esta misma lista del lado del cliente — no hay necesidad de
 * mandarlos aparte y arriesgar que se desincronicen del listado real.
 */
public record WallReactionsResponse(List<WallReactionItemResponse> reactions) {

    public static WallReactionsResponse from(List<ReaccionVista> vistas) {
        return new WallReactionsResponse(vistas.stream().map(WallReactionItemResponse::from).toList());
    }
}

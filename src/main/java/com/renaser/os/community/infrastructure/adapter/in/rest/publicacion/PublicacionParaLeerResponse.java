package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarPublicacionUseCase.PublicacionParaLeer;

import java.util.List;

/**
 * Una publicación del Muro para una ficha de seguimiento (spec 005): los nombres del Muro ({@code authorName},
 * {@code text}, {@code createdAt}, {@code media[{url, mimeType}]}) para que la app los lea como ya lee el feed, sin los
 * conteos de reacciones ni de comentarios.
 */
public record PublicacionParaLeerResponse(String id, String authorId, String authorName, String authorAvatarUrl,
                                          String text, String createdAt, List<MediaItemResponse> media) {

    static PublicacionParaLeerResponse from(PublicacionParaLeer publicacion) {
        return new PublicacionParaLeerResponse(publicacion.id().toString(), publicacion.autorId().toString(),
                publicacion.autorNombre(), publicacion.autorAvatarUrl(), publicacion.texto(),
                publicacion.creadaEn().toString(), publicacion.media().stream().map(MediaItemResponse::from).toList());
    }
}

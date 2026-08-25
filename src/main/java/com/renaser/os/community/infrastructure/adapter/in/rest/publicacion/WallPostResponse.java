package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarFeedUseCase.PublicacionVista;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.TipoPublicacion;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;

import java.util.List;
import java.util.Map;

/**
 * D-36: el wire habla el vocabulario VIEJO (ingles) que la app publicada ya consume —
 * {@code type}/{@code reactionCounts}/{@code myReactions} en ingles aunque el dominio y la
 * base esten en espanol (`tipo_publicacion`, `tipo_reaccion`). La traduccion vive SOLO
 * aca, nunca en dominio ni persistencia (mismo criterio que `support`/`rocks`,
 * ver TicketSoporteResponse.toWireEstado).
 */
public record WallPostResponse(String id, String authorId, String authorName, String authorAvatarUrl, String type,
                                String category, String text, List<MediaItemResponse> media, String createdAt,
                                Map<String, Integer> reactionCounts, List<String> myReactions, int commentCount) {

    public static WallPostResponse from(PublicacionVista vista) {
        Publicacion p = vista.publicacion();
        List<String> misReacciones = vista.miReaccion() == null ? List.of() : List.of(toWireTipo(vista.miReaccion()));
        return new WallPostResponse(p.id().toString(), p.autorId().toString(), vista.autorNombre(),
                vista.autorAvatarUrl(), toWireTipoPublicacion(p.tipo()), p.categoriaClave(), p.texto(),
                vista.media().stream().map(MediaItemResponse::from).toList(), p.creadoEn().toString(),
                Map.of("LIKE", vista.likes(), "DISLIKE", vista.dislikes()), misReacciones,
                vista.cantidadComentarios());
    }

    private static String toWireTipoPublicacion(TipoPublicacion tipo) {
        return switch (tipo) {
            case MANUAL -> "MANUAL";
            case HITO_AUTOMATICO -> "MILESTONE_AUTO";
            case GUERRERO_CAIDO -> "GUERRERO_CAIDO";
        };
    }

    private static String toWireTipo(TipoReaccion tipo) {
        return switch (tipo) {
            case ME_GUSTA -> "LIKE";
            case NO_ME_GUSTA -> "DISLIKE";
        };
    }
}

package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ConsultarReaccionesUseCase.ReaccionVista;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;

/**
 * Una fila de "quien reacciono". {@code type} sigue el mismo criterio D-36 que
 * {@link WallPostResponse}: el wire habla LIKE/DISLIKE en ingles aunque el dominio hable
 * ME_GUSTA/NO_ME_GUSTA en español. {@code role} viaja tal cual el nombre del enum publico
 * {@code users.api.UserRole} (TRAINEE/MENTOR/MENTOR_LEAD/ADMIN/ALCHEMIST) sin traducir: el
 * frontend ya tiene su propia traduccion a español para el subtitulo
 * (`chat/api/chatMappers.ts:ETIQUETA_ROL`), asi que no hace falta duplicarla del lado del
 * servidor.
 */
public record WallReactionItemResponse(String userId, String name, String avatarUrl, String role, String type) {

    public static WallReactionItemResponse from(ReaccionVista vista) {
        return new WallReactionItemResponse(vista.usuarioId().toString(), vista.nombre(), vista.avatarUrl(),
                vista.rol() != null ? vista.rol().name() : null, toWireTipo(vista.tipo()));
    }

    private static String toWireTipo(TipoReaccion tipo) {
        return switch (tipo) {
            case ME_GUSTA -> "LIKE";
            case NO_ME_GUSTA -> "DISLIKE";
        };
    }
}

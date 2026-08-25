package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase.ResultadoReaccion;

import java.util.Map;

public record WallReactionToggleResponse(boolean reacted, Map<String, Integer> reactionCounts) {

    public static WallReactionToggleResponse from(ResultadoReaccion resultado) {
        return new WallReactionToggleResponse(resultado.reaccionado(),
                Map.of("LIKE", resultado.likes(), "DISLIKE", resultado.dislikes()));
    }
}

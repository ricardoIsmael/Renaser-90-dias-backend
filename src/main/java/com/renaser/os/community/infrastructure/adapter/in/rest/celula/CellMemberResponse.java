package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;

/** Sin {@code coherenceScore} — vive en `puntajes_participante`, tabla de `points`
 * (docs/MODULO_COMMUNITY.md sec. 6). */
public record CellMemberResponse(String traineeId, String fullName, String avatarUrl, boolean isSelf) {

    public static CellMemberResponse from(PerfilBasico perfil, boolean isSelf) {
        return new CellMemberResponse(perfil.id().toString(), perfil.nombreCompleto(), perfil.avatarUrl(), isSelf);
    }
}

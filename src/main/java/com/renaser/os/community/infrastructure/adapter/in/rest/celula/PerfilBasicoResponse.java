package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;

public record PerfilBasicoResponse(String id, String fullName, String avatarUrl) {

    public static PerfilBasicoResponse from(PerfilBasico perfil) {
        if (perfil == null) {
            return null;
        }
        return new PerfilBasicoResponse(perfil.id().toString(), perfil.nombreCompleto(), perfil.avatarUrl());
    }
}

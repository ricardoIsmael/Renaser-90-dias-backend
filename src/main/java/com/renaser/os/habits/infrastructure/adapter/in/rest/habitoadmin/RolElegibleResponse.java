package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarRolesElegiblesUseCase.RolElegible;

/** Un rol elegible como audiencia: código de la API y nombre visible del servidor (spec 001, US5). */
public record RolElegibleResponse(String code, String name) {

    public static RolElegibleResponse from(RolElegible rol) {
        return new RolElegibleResponse(rol.codigo(), rol.nombre());
    }
}

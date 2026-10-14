package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.api.RolPersonalizadoAsignado;

/**
 * {@code rolPersonalizado: { id, nombre } | null} (spec 002, fase 2, FR-032; D-269): el rol personalizado vigente de
 * una cuenta, aditivo en {@code /auth/me}, {@code POST /users/me} y las listas de Administración. {@code role} sigue
 * siendo el rol base: una app que no conoce este campo (1.5.0) lo ignora y muestra el rol base.
 */
public record RolPersonalizadoResponse(String id, String nombre) {

    public static RolPersonalizadoResponse de(RolPersonalizadoAsignado rol) {
        return rol == null ? null : new RolPersonalizadoResponse(rol.id().toString(), rol.nombre());
    }
}

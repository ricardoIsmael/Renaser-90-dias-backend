package com.renaser.os.users.application.services;

import com.renaser.os.users.api.RolPersonalizadoResumen;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;

/** Del agregado al resumen de {@code users.api} (D-269). */
final class ResumenDeRolPersonalizado {

    private ResumenDeRolPersonalizado() {
    }

    static RolPersonalizadoResumen de(RolPersonalizado rol, long personas) {
        return new RolPersonalizadoResumen(rol.id(), rol.clave(), rol.nombre(), rol.descripcion(), rol.rolBase(),
                rol.activo(), rol.funcionalidades(), personas, rol.version(), rol.actualizadoPor(),
                rol.actualizadoEn());
    }
}

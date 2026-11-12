package com.renaser.os.users.application.ports.out.matriz;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.users.api.UserRole;

/**
 * Lo que la matriz estricta de MENTOR y TRAINEE_GUIDE negó, o habría negado en modo sombra: el rol, el permiso y el endpoint
 * (método y ruta con su plantilla, {@code /api/v1/mentor/groups/{id}}, no la ruta concreta). <b>Nunca</b> la cuenta: con eso alcanza
 * para corregir la matriz y no se guarda quién es nadie (spec 050, historia 8).
 */
public record DenegacionDeLaMatriz(ModoDeLaMatriz modo, UserRole rol, Permission permiso, String metodo, String ruta) {

    /** La etiqueta que ve quien lee el registro: lo que habría pasado en sombra, o lo que pasó. */
    public String estado() {
        return modo == ModoDeLaMatriz.SOMBRA ? "HABRIA_DENEGADO" : "DENEGADO";
    }
}

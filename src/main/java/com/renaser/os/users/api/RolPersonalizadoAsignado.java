package com.renaser.os.users.api;

import java.util.Objects;
import java.util.UUID;

/**
 * El rol personalizado de una cuenta, para mostrarlo (spec 002, FR-032; D-272): {@code rolPersonalizado: { id, nombre }}
 * en {@code /auth/me}, {@code POST /users/me}, las listas de Administración y la info de integrantes del chat. Solo
 * existe si el rol está activo y hereda del rol de la cuenta; {@code role} sigue siendo el rol base.
 */
public record RolPersonalizadoAsignado(UUID id, String nombre) {

    public RolPersonalizadoAsignado {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(nombre, "nombre es obligatorio");
    }
}

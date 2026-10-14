package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;

/**
 * Una cuenta de prueba de «ver como rol» lista para abrirle una sesión de solo lectura (spec 002, fase 2, US5; D-270).
 *
 * @param rol              el rol base de la cuenta (con un rol personalizado, el de su base)
 * @param rolPersonalizado el rol personalizado que lleva, o {@code null} si es la de un rol base
 */
public record CuentaDePrueba(UserId id, String nombre, UserRole rol, RolPersonalizadoAsignado rolPersonalizado) {

    public CuentaDePrueba {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        Objects.requireNonNull(rol, "rol es obligatorio");
    }
}

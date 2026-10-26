package com.renaser.os.plataforma.domain.model.roles;

import com.renaser.os.users.api.UserRole;

import java.util.Objects;
import java.util.Set;

/**
 * Lo que la regla de disponibilidad necesita de un rol personalizado activo (D-272): de qué rol hereda y qué claves
 * marcó.
 */
public record RolEnMemoria(UserRole rolBase, Set<String> funcionalidades) {

    public RolEnMemoria {
        Objects.requireNonNull(rolBase, "rolBase es obligatorio");
        funcionalidades = Set.copyOf(funcionalidades);
    }
}

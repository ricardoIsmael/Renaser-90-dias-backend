package com.renaser.os.habits.domain.model.audiencia;

import java.util.Set;

/**
 * A qué roles aplica un hábito global. Sin roles = para todos, incluidos los roles que se agreguen después (spec 001,
 * FR-002). Marcar todos los roles de hoy NO es lo mismo: un rol futuro no lo recibe (clarify 2026-10-12).
 */
public record Audiencia(Set<CodigoDeRol> roles) {

    public static final Audiencia PARA_TODOS = new Audiencia(Set.of());

    public Audiencia {
        roles = Set.copyOf(roles);
    }

    public static Audiencia de(Set<CodigoDeRol> roles) {
        return roles.isEmpty() ? PARA_TODOS : new Audiencia(roles);
    }

    public boolean paraTodos() {
        return roles.isEmpty();
    }

    public boolean aplicaA(CodigoDeRol rol) {
        return paraTodos() || roles.contains(rol);
    }
}

package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.users.api.UserRole;

/** La etiqueta del enum {@code rol_usuario} de la base (en español) traducida al rol del dominio. */
final class RolDeLaBase {

    private RolDeLaBase() {
    }

    static UserRole aRol(String etiqueta) {
        return switch (etiqueta) {
            case "ALQUIMISTA" -> UserRole.ALCHEMIST;
            case "ADMIN" -> UserRole.ADMIN;
            case "LIDER_MENTORES" -> UserRole.MENTOR_LEAD;
            case "MENTOR" -> UserRole.MENTOR;
            case "GUIA_APRENDIZ" -> UserRole.TRAINEE_GUIDE;
            case "APRENDIZ" -> UserRole.TRAINEE;
            case "SOPORTE_TI" -> UserRole.IT_SUPPORT;
            default -> throw new IllegalStateException("Rol de usuario desconocido: " + etiqueta);
        };
    }
}

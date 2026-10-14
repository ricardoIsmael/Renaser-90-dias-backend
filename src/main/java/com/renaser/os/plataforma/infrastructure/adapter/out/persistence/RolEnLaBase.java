package com.renaser.os.plataforma.infrastructure.adapter.out.persistence;

import com.renaser.os.users.api.UserRole;

/**
 * {@link UserRole} (API, inglés) &lt;-&gt; {@code renaser.rol_usuario} (base, español, D-21), para las excepciones por
 * rol de {@code funcionalidades_estado_rol}. Espejo de {@code users.UserPersistenceMapper}: un rol nuevo se suma acá
 * también (el {@code switch} de ida lo marca el compilador; el de vuelta, por texto, falla al leer la fila — D-265).
 */
final class RolEnLaBase {

    private RolEnLaBase() {
    }

    static String aClave(UserRole rol) {
        return switch (rol) {
            case IT_SUPPORT -> "SOPORTE_TI";
            case ALCHEMIST -> "ALQUIMISTA";
            case ADMIN -> "ADMIN";
            case MENTOR_LEAD -> "LIDER_MENTORES";
            case MENTOR -> "MENTOR";
            case TRAINEE_GUIDE -> "GUIA_APRENDIZ";
            case TRAINEE -> "APRENDIZ";
        };
    }

    static UserRole deClave(String clave) {
        return switch (clave) {
            case "SOPORTE_TI" -> UserRole.IT_SUPPORT;
            case "ALQUIMISTA" -> UserRole.ALCHEMIST;
            case "ADMIN" -> UserRole.ADMIN;
            case "LIDER_MENTORES" -> UserRole.MENTOR_LEAD;
            case "MENTOR" -> UserRole.MENTOR;
            case "GUIA_APRENDIZ" -> UserRole.TRAINEE_GUIDE;
            case "APRENDIZ" -> UserRole.TRAINEE;
            default -> throw new IllegalStateException("Rol de usuario desconocido: " + clave);
        };
    }
}

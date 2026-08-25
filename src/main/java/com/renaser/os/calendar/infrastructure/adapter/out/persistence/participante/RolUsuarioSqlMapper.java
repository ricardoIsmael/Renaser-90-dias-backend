package com.renaser.os.calendar.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;

/** Traduccion {@link RolUsuario} (dominio, ingles) <-> {@code usuarios.rol} (Postgres,
 * español, D-21) — compartida entre los adaptadores de este paquete que leen `usuarios`
 * directo (no via {@code renaser.roles}, que es solo para {@code roles_destino_evento}). */
public final class RolUsuarioSqlMapper {

    private RolUsuarioSqlMapper() {
    }

    public static String aClave(RolUsuario rol) {
        return switch (rol) {
            case ALCHEMIST -> "ALQUIMISTA";
            case ADMIN -> "ADMIN";
            case MENTOR_LEAD -> "LIDER_MENTORES";
            case MENTOR -> "MENTOR";
            case TRAINEE -> "APRENDIZ";
        };
    }

    public static RolUsuario deClave(String clave) {
        return switch (clave) {
            case "ALQUIMISTA" -> RolUsuario.ALCHEMIST;
            case "ADMIN" -> RolUsuario.ADMIN;
            case "LIDER_MENTORES" -> RolUsuario.MENTOR_LEAD;
            case "MENTOR" -> RolUsuario.MENTOR;
            case "APRENDIZ" -> RolUsuario.TRAINEE;
            default -> throw new IllegalStateException("Rol de usuario desconocido: " + clave);
        };
    }
}

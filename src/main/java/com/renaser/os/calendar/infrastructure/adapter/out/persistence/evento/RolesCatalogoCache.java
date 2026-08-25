package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code roles_destino_evento.rol_id} es {@code smallint REFERENCES roles(id)} — la BD NO
 * se toca en esta fase (decision del dueño del proyecto: el baseline de 90 tablas es
 * definitivo, sin migraciones nuevas). La traduccion {@code rol_id} <-> {@link RolUsuario}
 * vive aca, en el adaptador, no en una migracion.
 *
 * <p>{@code renaser.roles} son 5 filas FIJAS, sembradas en el baseline y sin escritura
 * (es_sistema=true, ON DELETE RESTRICT) — se cargan UNA vez al arranque en un {@code Map}
 * inmutable, mas simple y mas rapido que un JOIN en cada consulta de audiencia. Mapeo
 * {@code roles.clave} <-> {@link RolUsuario}: espejo de
 * {@code users/infrastructure/adapter/out/persistence/user/RolUsuarioJpa} (D-21).
 */
@Component
class RolesCatalogoCache {

    private final JdbcTemplate jdbcTemplate;
    private Map<RolUsuario, Short> idPorRol;
    private Map<Short, RolUsuario> rolPorId;

    RolesCatalogoCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void cargar() {
        Map<RolUsuario, Short> porRol = new EnumMap<>(RolUsuario.class);
        Map<Short, RolUsuario> porId = new HashMap<>();
        jdbcTemplate.query("SELECT id, clave FROM renaser.roles", rs -> {
            short id = rs.getShort("id");
            RolUsuario rol = aRolUsuario(rs.getString("clave"));
            porRol.put(rol, id);
            porId.put(id, rol);
        });
        this.idPorRol = Map.copyOf(porRol);
        this.rolPorId = Map.copyOf(porId);
    }

    short idDe(RolUsuario rol) {
        Short id = idPorRol.get(rol);
        if (id == null) {
            throw new IllegalStateException("renaser.roles no tiene fila para " + rol + " — catalogo desincronizado");
        }
        return id;
    }

    RolUsuario rolDe(short id) {
        RolUsuario rol = rolPorId.get(id);
        if (rol == null) {
            throw new IllegalStateException("renaser.roles no tiene fila con id=" + id + " — catalogo desincronizado");
        }
        return rol;
    }

    private static RolUsuario aRolUsuario(String clave) {
        return switch (clave) {
            case "ALQUIMISTA" -> RolUsuario.ALCHEMIST;
            case "ADMIN" -> RolUsuario.ADMIN;
            case "LIDER_MENTORES" -> RolUsuario.MENTOR_LEAD;
            case "MENTOR" -> RolUsuario.MENTOR;
            case "APRENDIZ" -> RolUsuario.TRAINEE;
            default -> throw new IllegalStateException("clave de rol desconocida en renaser.roles: " + clave);
        };
    }
}

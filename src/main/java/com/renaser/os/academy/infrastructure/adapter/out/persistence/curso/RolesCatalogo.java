package com.renaser.os.academy.infrastructure.adapter.out.persistence.curso;

import com.renaser.os.users.api.UserRole;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce {@code roles_permitidos_curso.rol_id} (smallint, FK a
 * {@code renaser.roles}) hacia {@link UserRole} — decision AC-05, "la base
 * de datos es inmutable en esta fase" (`docs/MODULO_ACADEMY.md` §5): la
 * tabla `roles` esta [SUPERADA] por D-21
 * en el resto del sistema (el RBAC real vive en el enum Java), pero
 * `roles_permitidos_curso` todavia apunta a ella por FK y esa columna NO se
 * toca en esta fase. En vez de duplicar la matriz de 5 filas a mano, se leen
 * de `renaser.roles` (sembradas en el baseline, V1__baseline_renaser.sql:1546-1551)
 * y se cachean en memoria — son datos de sistema que no cambian en caliente.
 *
 * <p>Cache perezosa con doble-check bajo `synchronized`: 5 filas, se carga
 * una sola vez por instancia de esta clase (singleton de Spring), sin pegarle
 * a la base en cada lectura de curso.
 */
@Component
class RolesCatalogo {

    private final EntityManager entityManager;
    private volatile Map<Short, UserRole> porId;

    RolesCatalogo(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** {@code null} si el id no corresponde a ninguna fila de `roles` (dato inconsistente, no debería pasar). */
    UserRole claveDe(short rolId) {
        return mapa().get(rolId);
    }

    private Map<Short, UserRole> mapa() {
        Map<Short, UserRole> actual = porId;
        if (actual != null) {
            return actual;
        }
        synchronized (this) {
            if (porId == null) {
                porId = cargar();
            }
            return porId;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Short, UserRole> cargar() {
        List<Object[]> filas = entityManager.createNativeQuery("SELECT id, clave FROM renaser.roles").getResultList();
        Map<Short, UserRole> mapa = new HashMap<>();
        for (Object[] fila : filas) {
            short id = ((Number) fila[0]).shortValue();
            mapa.put(id, aUserRole(String.valueOf(fila[1])));
        }
        return Map.copyOf(mapa);
    }

    /** Espejo de `users/infrastructure/adapter/out/persistence/user/RolUsuarioJpa.java`. */
    private static UserRole aUserRole(String clave) {
        return switch (clave) {
            case "ALQUIMISTA" -> UserRole.ALCHEMIST;
            case "ADMIN" -> UserRole.ADMIN;
            case "LIDER_MENTORES" -> UserRole.MENTOR_LEAD;
            case "MENTOR" -> UserRole.MENTOR;
            case "APRENDIZ" -> UserRole.TRAINEE;
            default -> throw new IllegalStateException("Rol desconocido en renaser.roles: " + clave);
        };
    }
}

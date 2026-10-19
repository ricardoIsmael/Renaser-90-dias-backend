package com.renaser.os.plataforma.domain.model.vercomo;

import com.renaser.os.users.api.UserRole;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * El rol que Soporte TI eligió mirar con «ver como» (spec 002, fase 2, US5; D-273): uno de los seis roles base o un rol
 * personalizado activo (P4).
 */
public sealed interface RolElegido {

    /**
     * La foto de la auditoría {@code VER_COMO} (la forma que lee la consola de la app): {@code {rol: "MENTOR"}} o
     * {@code {rolPersonalizado: {id, nombre}}}.
     */
    Map<String, Object> foto();

    /** Uno de los seis roles base, nunca Soporte TI. */
    record Base(UserRole rol) implements RolElegido {

        public Base {
            Objects.requireNonNull(rol, "rol es obligatorio");
            if (rol == UserRole.IT_SUPPORT) {
                throw new IllegalArgumentException("Elige uno de los seis roles: Soporte TI no se mira con «ver como»");
            }
        }

        @Override
        public Map<String, Object> foto() {
            return Map.of("rol", rol.name());
        }
    }

    /** Un rol personalizado, con el nombre que tenía al abrir la sesión. */
    record Personalizado(UUID id, String nombre) implements RolElegido {

        public Personalizado {
            Objects.requireNonNull(id, "id es obligatorio");
            Objects.requireNonNull(nombre, "nombre es obligatorio");
        }

        @Override
        public Map<String, Object> foto() {
            Map<String, Object> rol = new LinkedHashMap<>();
            rol.put("id", id.toString());
            rol.put("nombre", nombre);
            return Map.of("rolPersonalizado", rol);
        }
    }
}

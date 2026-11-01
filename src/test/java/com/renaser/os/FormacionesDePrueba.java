package com.renaser.os;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;

/**
 * Lo que hace la aplicación por una persona del personal que se da de alta (spec 022): asignarle la Escuela como formación activa. Las pruebas de integración que
 * siembran cuentas con SQL a mano se saltan ese paso, y sin él el mentor, la guía o el líder no ven ninguna formación (a propósito: sin asignación no se ve nada).
 */
public final class FormacionesDePrueba {

    private static final Set<String> PERSONAL = Set.of("MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES");

    private FormacionesDePrueba() {
    }

    /** Si el rol es de personal con formaciones asignadas, lo deja en la Escuela como activa; con cualquier otro rol no hace nada. */
    public static void alPersonalLeAsignaLaEscuela(JdbcTemplate jdbc, UUID usuario, String rol) {
        if (PERSONAL.contains(rol)) {
            jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'ESCUELA', true) ON CONFLICT DO NOTHING",
                    usuario);
        }
    }
}

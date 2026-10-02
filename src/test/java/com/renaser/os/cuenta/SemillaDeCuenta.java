package com.renaser.os.cuenta;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Siembra una persona con al menos una fila en cada tabla de los módulos que implementan
 * {@code BorradoDeDatosDeCuenta} ({@code cuenta/semilla-de-una-persona.sql}) y cuenta las filas de todo el
 * esquema para comparar antes y después de un borrado.
 */
final class SemillaDeCuenta {

    private final JdbcTemplate jdbc;

    SemillaDeCuenta(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    UUID nuevaPersona() {
        UUID persona = UUID.randomUUID();
        String sql = leer("cuenta/semilla-de-una-persona.sql").replace("{{P}}", persona.toString());
        jdbc.execute((Connection conexion) -> {
            ScriptUtils.executeSqlScript(conexion, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            return null;
        });
        return persona;
    }

    /** Filas de cada tabla del esquema {@code renaser} (sin {@code shedlock}, que escriben los barridos). */
    Map<String, Long> filasPorTabla() {
        List<String> tablas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'renaser' AND table_type = 'BASE TABLE'
                   AND table_name NOT IN ('shedlock', 'flyway_schema_history')
                 ORDER BY table_name
                """, String.class);
        Map<String, Long> filas = new LinkedHashMap<>();
        tablas.forEach(t -> filas.put(t, jdbc.queryForObject("SELECT count(*) FROM renaser." + t, Long.class)));
        return filas;
    }

    /**
     * Toda columna del esquema que guarda el id de una persona: las que tienen FK a {@code usuarios} o a
     * {@code participantes_programa}, más las que no tienen FK y se sabe que son de una persona.
     */
    List<Map<String, Object>> columnasDePersona() {
        return jdbc.queryForList("""
                SELECT kcu.table_name AS tabla, kcu.column_name AS columna
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.key_column_usage kcu
                    ON kcu.constraint_name = tc.constraint_name AND kcu.table_schema = tc.table_schema
                  JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
                 WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'renaser'
                   AND ccu.table_name IN ('usuarios', 'participantes_programa')
                UNION
                SELECT 'anomalias_acompanamiento', 'usuario_id'
                UNION
                SELECT 'revisiones_semanales_sin_celular', 'participante_id'
                """);
    }

    long filasCon(String tabla, String columna, UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser." + tabla + " WHERE " + columna + " = ?",
                Long.class, persona);
    }

    private static String leer(String recurso) {
        try {
            return new ClassPathResource(recurso).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

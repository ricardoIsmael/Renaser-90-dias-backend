package com.renaser.os.users.infrastructure.adapter.out.persistence.rolpersonalizado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import com.renaser.os.users.application.ports.out.rolpersonalizado.GuardarRolesPersonalizadosPort;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code roles_personalizados}, sus funcionalidades y {@code usuarios.rol_personalizado_id} (V105, D-269), con
 * {@link JdbcClient}: tablas chicas (hasta 20 roles activos) que se leen enteras para la consola y para la copia en
 * memoria de la plataforma.
 *
 * <p>«Vigente» = el rol está activo y hereda del rol de la cuenta ({@code r.rol_base = u.rol}). La segunda condición es
 * un cinturón: la app quita el rol personalizado al cambiar el rol base ({@code User.changeRole}), pero un cambio por
 * fuera (un script de la base) podría dejar una cuenta con un rol que hereda de otro; ese no restringe ni se muestra.
 */
@Component
class RolesPersonalizadosJdbcAdapter implements CargarRolesPersonalizadosPort, GuardarRolesPersonalizadosPort {

    /** La clave del candado de transacción de las escrituras de roles personalizados ({@code pg_advisory_xact_lock}). */
    static final long CANDADO_DE_ESCRITURAS = 2_690_105L;

    private static final String COLUMNAS = """
            SELECT id, nombre, descripcion, rol_base::text AS rol_base, activo, version, creado_por, creado_en,
                   actualizado_por, actualizado_en
              FROM renaser.roles_personalizados
            """;
    private static final String VIGENTES = """
            SELECT u.id, u.rol_personalizado_id
              FROM renaser.usuarios u
              JOIN renaser.roles_personalizados r ON r.id = u.rol_personalizado_id
             WHERE r.activo AND r.rol_base = u.rol
            """;

    private final JdbcClient jdbc;

    RolesPersonalizadosJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RolPersonalizado> porId(UUID id) {
        Set<String> claves = new HashSet<>(jdbc.sql("SELECT clave FROM renaser.roles_personalizados_funcionalidades "
                + "WHERE rol_id = :id").param("id", id).query(String.class).list());
        return jdbc.sql(COLUMNAS + " WHERE id = :id").param("id", id).query((rs, n) -> leer(rs, claves)).optional();
    }

    @Override
    public List<RolPersonalizado> todos() {
        Map<UUID, Set<String>> claves = new HashMap<>();
        jdbc.sql("SELECT rol_id, clave FROM renaser.roles_personalizados_funcionalidades")
                .query((rs, n) -> claves.computeIfAbsent(rs.getObject("rol_id", UUID.class), id -> new HashSet<>())
                        .add(rs.getString("clave")))
                .list();
        return jdbc.sql(COLUMNAS + " ORDER BY lower(nombre), id")
                .query((rs, n) -> leer(rs, claves.getOrDefault(rs.getObject("id", UUID.class), Set.of())))
                .list();
    }

    @Override
    public Map<UUID, Long> personasPorRol() {
        Map<UUID, Long> personas = new HashMap<>();
        jdbc.sql("SELECT rol_personalizado_id, count(*) AS personas FROM renaser.usuarios "
                        + "WHERE rol_personalizado_id IS NOT NULL GROUP BY rol_personalizado_id")
                .query((rs, n) -> personas.put(rs.getObject("rol_personalizado_id", UUID.class),
                        rs.getLong("personas")))
                .list();
        return personas;
    }

    @Override
    public long personasCon(UUID rolId) {
        return jdbc.sql("SELECT count(*) FROM renaser.usuarios WHERE rol_personalizado_id = :rol")
                .param("rol", rolId).query(Long.class).single();
    }

    @Override
    public Map<UserId, UUID> asignacionesVigentes() {
        Map<UserId, UUID> asignaciones = new LinkedHashMap<>();
        jdbc.sql(VIGENTES).query((rs, n) -> asignaciones.put(UserId.of(rs.getObject("id", UUID.class)),
                rs.getObject("rol_personalizado_id", UUID.class))).list();
        return asignaciones;
    }

    @Override
    public Optional<UUID> vigenteDe(UserId usuario) {
        return jdbc.sql(VIGENTES + " AND u.id = :id").param("id", usuario.value())
                .query((rs, n) -> rs.getObject("rol_personalizado_id", UUID.class)).optional();
    }

    @Override
    public void bloquearCambios() {
        // La función devuelve void: se envuelve para leer un entero y no una columna sin tipo.
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:clave)) AS candado")
                .param("clave", CANDADO_DE_ESCRITURAS).query(Integer.class).single();
    }

    @Override
    public void insertar(RolPersonalizado rol) {
        jdbc.sql("""
                        INSERT INTO renaser.roles_personalizados
                               (id, clave, nombre, descripcion, rol_base, activo, version, creado_por, creado_en,
                                actualizado_por, actualizado_en)
                        VALUES (:id, :clave, :nombre, :descripcion, CAST(:rolBase AS renaser.rol_usuario), :activo,
                                :version, :creadoPor, :creadoEn, :actualizadoPor, :actualizadoEn)
                        """)
                .params(parametros(rol)).param("clave", rol.clave())
                .param("creadoPor", rol.creadoPor().value()).param("creadoEn", Timestamp.from(rol.creadoEn()))
                .update();
        insertarClaves(rol);
    }

    @Override
    public boolean actualizar(RolPersonalizado rol, long versionEsperada) {
        int filas = jdbc.sql("""
                        UPDATE renaser.roles_personalizados
                           SET nombre = :nombre, descripcion = :descripcion,
                               rol_base = CAST(:rolBase AS renaser.rol_usuario), activo = :activo, version = :version,
                               actualizado_por = :actualizadoPor, actualizado_en = :actualizadoEn
                         WHERE id = :id AND version = :esperada
                        """)
                .params(parametros(rol)).param("esperada", versionEsperada).update();
        if (filas == 0) {
            return false;
        }
        jdbc.sql("DELETE FROM renaser.roles_personalizados_funcionalidades WHERE rol_id = :id")
                .param("id", rol.id()).update();
        insertarClaves(rol);
        return true;
    }

    @Override
    public void borrar(UUID id) {
        jdbc.sql("DELETE FROM renaser.roles_personalizados WHERE id = :id").param("id", id).update();
    }

    @Override
    public int reemplazarAsignaciones(UUID origen, UUID destino) {
        return jdbc.sql("UPDATE renaser.usuarios SET rol_personalizado_id = :destino "
                        + "WHERE rol_personalizado_id = :origen")
                .param("destino", destino, java.sql.Types.OTHER).param("origen", origen).update();
    }

    private void insertarClaves(RolPersonalizado rol) {
        for (String clave : rol.funcionalidades()) {
            jdbc.sql("INSERT INTO renaser.roles_personalizados_funcionalidades (rol_id, clave) VALUES (:rol, :clave)")
                    .param("rol", rol.id()).param("clave", clave).update();
        }
    }

    private static Map<String, Object> parametros(RolPersonalizado rol) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("id", rol.id());
        parametros.put("nombre", rol.nombre());
        parametros.put("descripcion", rol.descripcion());
        parametros.put("rolBase", RolEnLaBase.aClave(rol.rolBase()));
        parametros.put("activo", rol.activo());
        parametros.put("version", rol.version());
        parametros.put("actualizadoPor", rol.actualizadoPor().value());
        parametros.put("actualizadoEn", Timestamp.from(rol.actualizadoEn()));
        return parametros;
    }

    private static RolPersonalizado leer(ResultSet rs, Set<String> claves) throws SQLException {
        return RolPersonalizado.rehidratar(rs.getObject("id", UUID.class), rs.getString("nombre"),
                rs.getString("descripcion"), RolEnLaBase.deClave(rs.getString("rol_base")), rs.getBoolean("activo"),
                claves, rs.getLong("version"), UserId.of(rs.getObject("creado_por", UUID.class)),
                rs.getTimestamp("creado_en").toInstant(), UserId.of(rs.getObject("actualizado_por", UUID.class)),
                rs.getTimestamp("actualizado_en").toInstant());
    }

    /** {@link UserRole} &lt;-&gt; {@code rol_usuario}, espejo de {@code UserPersistenceMapper} (un rol nuevo, acá también). */
    static final class RolEnLaBase {

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
}

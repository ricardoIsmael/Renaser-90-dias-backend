package com.renaser.os.plataforma.infrastructure.adapter.out.persistence;

import com.renaser.os.plataforma.application.ports.out.CargarEstadosPort;
import com.renaser.os.plataforma.application.ports.out.GuardarEstadoPort;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code funcionalidades_estado} y {@code funcionalidades_estado_rol} (V101) con {@code JdbcClient}: dos tablas chicas
 * que se leen enteras cada 30 s y en cada cambio, sin necesidad de entidades JPA.
 */
@Component
class EstadosDeFuncionalidadesJdbcAdapter implements CargarEstadosPort, GuardarEstadoPort {

    private static final String TODOS = """
            SELECT e.clave, e.prendida_global, e.aviso, e.version, e.actualizado_por, e.actualizado_en,
                   r.rol::text AS rol
              FROM renaser.funcionalidades_estado e
              LEFT JOIN renaser.funcionalidades_estado_rol r ON r.clave = e.clave AND NOT r.prendida
             ORDER BY e.clave
            """;
    private static final String INSERTAR = """
            INSERT INTO renaser.funcionalidades_estado
                   (clave, prendida_global, aviso, version, actualizado_por, actualizado_en)
            VALUES (:clave, :prendida, :aviso, :version, :por, :en)
            ON CONFLICT (clave) DO NOTHING
            """;
    private static final String ACTUALIZAR = """
            UPDATE renaser.funcionalidades_estado
               SET prendida_global = :prendida, aviso = :aviso, version = :version,
                   actualizado_por = :por, actualizado_en = :en
             WHERE clave = :clave AND version = :esperada
            """;
    private static final String BORRAR_ROLES = "DELETE FROM renaser.funcionalidades_estado_rol WHERE clave = :clave";
    private static final String INSERTAR_ROL = """
            INSERT INTO renaser.funcionalidades_estado_rol (clave, rol, prendida)
            VALUES (:clave, CAST(:rol AS renaser.rol_usuario), false)
            """;

    private final JdbcClient jdbc;

    EstadosDeFuncionalidadesJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EstadoDeFuncionalidad> todos() {
        Map<String, Fila> filas = new LinkedHashMap<>();
        jdbc.sql(TODOS).query((ResultSet rs) -> {
            Fila fila = filas.computeIfAbsent(rs.getString("clave"), clave -> leer(rs));
            String rol = rs.getString("rol");
            if (rol != null) {
                fila.apagadaPara().add(RolEnLaBase.deClave(rol));
            }
        });
        List<EstadoDeFuncionalidad> estados = new ArrayList<>();
        filas.values().forEach(fila -> estados.add(fila.aEstado()));
        return estados;
    }

    @Override
    public long siguienteVersion() {
        return jdbc.sql("SELECT nextval('renaser.funcionalidades_version_seq')").query(Long.class).single();
    }

    @Override
    public boolean guardar(EstadoDeFuncionalidad nuevo, long versionEsperada) {
        int filas = jdbc.sql(versionEsperada == 0L ? INSERTAR : ACTUALIZAR)
                .param("clave", nuevo.clave()).param("prendida", nuevo.prendidaGlobal())
                .param("aviso", nuevo.aviso()).param("version", nuevo.version())
                .param("por", nuevo.actualizadoPor().value()).param("en", Timestamp.from(nuevo.actualizadoEn()))
                .param("esperada", versionEsperada)
                .update();
        if (filas != 1) {
            return false;
        }
        jdbc.sql(BORRAR_ROLES).param("clave", nuevo.clave()).update();
        for (UserRole rol : nuevo.apagadaPara()) {
            jdbc.sql(INSERTAR_ROL).param("clave", nuevo.clave()).param("rol", RolEnLaBase.aClave(rol)).update();
        }
        return true;
    }

    private static Fila leer(ResultSet rs) {
        try {
            return new Fila(rs.getString("clave"), rs.getBoolean("prendida_global"), EnumSet.noneOf(UserRole.class),
                    rs.getString("aviso"), rs.getLong("version"), rs.getObject("actualizado_por", UUID.class),
                    rs.getTimestamp("actualizado_en"));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer el estado de una funcionalidad", e);
        }
    }

    private record Fila(String clave, boolean prendida, Set<UserRole> apagadaPara, String aviso, long version,
                        UUID por, Timestamp en) {

        EstadoDeFuncionalidad aEstado() {
            return new EstadoDeFuncionalidad(clave, prendida, apagadaPara, aviso, version, UserId.of(por),
                    en.toInstant());
        }
    }
}

package com.renaser.os.plataforma.infrastructure.adapter.out.persistence;

import com.renaser.os.plataforma.application.ports.out.CargarDesplieguesPort;
import com.renaser.os.plataforma.application.ports.out.GuardarDesplieguePort;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code funcionalidades_despliegue} y {@code funcionalidades_probadores} (V103) con {@code JdbcClient}: dos tablas
 * chicas que se leen enteras cada 30 s y en cada cambio. El bloqueo optimista va en el SQL ({@code WHERE version =}),
 * así que dos consolas que mueven el escalón a la vez no se pisan: la segunda no toca ninguna fila y es un 409.
 */
@Component
class DesplieguesJdbcAdapter implements CargarDesplieguesPort, GuardarDesplieguePort {

    private static final String SELECCION = """
            SELECT d.clave, d.porcentaje, d.version, d.iniciado_en, d.actualizado_por, d.actualizado_en,
                   p.usuario_id AS probadora
              FROM renaser.funcionalidades_despliegue d
              LEFT JOIN renaser.funcionalidades_probadores p ON p.clave = d.clave
            """;
    private static final String TODOS = SELECCION + " ORDER BY d.clave";
    private static final String UNO = SELECCION + " WHERE d.clave = :clave";
    private static final String EMPEZAR = """
            INSERT INTO renaser.funcionalidades_despliegue
                   (clave, porcentaje, version, iniciado_en, actualizado_por, actualizado_en)
            VALUES (:clave, :porcentaje, :version, :iniciado, :por, :en)
            ON CONFLICT (clave) DO NOTHING
            """;
    private static final String CAMBIAR = """
            UPDATE renaser.funcionalidades_despliegue
               SET porcentaje = :porcentaje, version = :version, actualizado_por = :por, actualizado_en = :en
             WHERE clave = :clave AND version = :esperada
            """;
    private static final String TERMINAR =
            "DELETE FROM renaser.funcionalidades_despliegue WHERE clave = :clave AND version = :esperada";
    // Solo si la funcionalidad sigue en despliegue: si terminó en el medio, no inserta (y la FK no revienta).
    private static final String SUMAR = """
            INSERT INTO renaser.funcionalidades_probadores (clave, usuario_id, sumado_por, sumado_en)
            SELECT :clave, :usuario, :por, :en
             WHERE EXISTS (SELECT 1 FROM renaser.funcionalidades_despliegue WHERE clave = :clave)
            ON CONFLICT (clave, usuario_id) DO NOTHING
            """;
    private static final String QUITAR =
            "DELETE FROM renaser.funcionalidades_probadores WHERE clave = :clave AND usuario_id = :usuario";

    private final JdbcClient jdbc;

    DesplieguesJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DespliegueDeFuncionalidad> todos() {
        return leer(jdbc.sql(TODOS));
    }

    @Override
    public Optional<DespliegueDeFuncionalidad> de(String clave) {
        return leer(jdbc.sql(UNO).param("clave", clave)).stream().findFirst();
    }

    @Override
    public long siguienteVersion() {
        return jdbc.sql("SELECT nextval('renaser.funcionalidades_version_seq')").query(Long.class).single();
    }

    @Override
    public boolean empezar(DespliegueDeFuncionalidad nuevo) {
        return jdbc.sql(EMPEZAR).param("clave", nuevo.clave()).param("porcentaje", nuevo.porcentaje())
                .param("version", nuevo.version()).param("iniciado", Timestamp.from(nuevo.iniciadoEn()))
                .param("por", nuevo.actualizadoPor().value()).param("en", Timestamp.from(nuevo.actualizadoEn()))
                .update() == 1;
    }

    @Override
    public boolean cambiarPorcentaje(DespliegueDeFuncionalidad nuevo, long versionEsperada) {
        return jdbc.sql(CAMBIAR).param("clave", nuevo.clave()).param("porcentaje", nuevo.porcentaje())
                .param("version", nuevo.version()).param("por", nuevo.actualizadoPor().value())
                .param("en", Timestamp.from(nuevo.actualizadoEn())).param("esperada", versionEsperada)
                .update() == 1;
    }

    @Override
    public boolean terminar(String clave, long versionEsperada) {
        return jdbc.sql(TERMINAR).param("clave", clave).param("esperada", versionEsperada).update() == 1;
    }

    @Override
    public boolean sumarProbadora(String clave, UserId persona, UserId por, Instant en) {
        return jdbc.sql(SUMAR).param("clave", clave).param("usuario", persona.value()).param("por", por.value())
                .param("en", Timestamp.from(en)).update() == 1;
    }

    @Override
    public boolean quitarProbadora(String clave, UserId persona) {
        return jdbc.sql(QUITAR).param("clave", clave).param("usuario", persona.value()).update() == 1;
    }

    private static List<DespliegueDeFuncionalidad> leer(JdbcClient.StatementSpec consulta) {
        Map<String, Fila> filas = new LinkedHashMap<>();
        consulta.query((ResultSet rs) -> {
            Fila fila = filas.computeIfAbsent(rs.getString("clave"), clave -> fila(rs));
            UUID probadora = rs.getObject("probadora", UUID.class);
            if (probadora != null) {
                fila.probadores().add(UserId.of(probadora));
            }
        });
        List<DespliegueDeFuncionalidad> despliegues = new ArrayList<>(filas.size());
        filas.values().forEach(fila -> despliegues.add(fila.aDespliegue()));
        return despliegues;
    }

    private static Fila fila(ResultSet rs) {
        try {
            return new Fila(rs.getString("clave"), rs.getInt("porcentaje"), new HashSet<>(), rs.getLong("version"),
                    rs.getTimestamp("iniciado_en"), rs.getObject("actualizado_por", UUID.class),
                    rs.getTimestamp("actualizado_en"));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer el despliegue de una funcionalidad", e);
        }
    }

    private record Fila(String clave, int porcentaje, Set<UserId> probadores, long version, Timestamp iniciado,
                        UUID por, Timestamp en) {

        DespliegueDeFuncionalidad aDespliegue() {
            return new DespliegueDeFuncionalidad(clave, porcentaje, probadores, version, iniciado.toInstant(),
                    UserId.of(por), en.toInstant());
        }
    }
}

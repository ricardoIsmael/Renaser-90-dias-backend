package com.renaser.os.calendar.infrastructure.adapter.out.persistence.meet;

import com.renaser.os.calendar.application.ports.out.meet.LoadSalaMeetPort;
import com.renaser.os.calendar.application.ports.out.meet.SaveSalaMeetPort;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.ArtefactosDeLaSala;
import com.renaser.os.calendar.domain.model.meet.EstadoDeCoanfitriones;
import com.renaser.os.calendar.domain.model.meet.EstadoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.PoliticaDeReintentos;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code eventos_meet} (V169 y V171, spec 040) con {@link JdbcClient}: una fila por evento y por fecha. Guardar es un upsert por id; la unicidad
 * {@code (evento_id, fecha_ocurrencia)} y la de {@code id_google} protegen contra dos salas para la misma fecha aunque dos hilos pidan a la vez.
 * Los instantes viajan como {@link OffsetDateTime} en UTC.
 */
@Component
class SalaMeetJdbcAdapter implements LoadSalaMeetPort, SaveSalaMeetPort {

    private static final String COLUMNAS = "id, evento_id, fecha_ocurrencia, id_google, enlace, estado, acceso_elegido, acceso_confirmado, "
            + "intentos, proximo_intento, ultimo_error, creada_en, actualizada_en, version, grabacion_confirmada, transcripcion_confirmada, "
            + "notas_confirmadas, coanfitriones_estado, coanfitriones_motivo";

    /** Lo que sigue faltando por hacer con Google: sin hacer, o con el acceso a medias. */
    private static final String CON_TRABAJO = "(estado IN ('PENDIENTE', 'FALLIDO', 'PENDIENTE_DE_SINCRONIZAR') "
            + "OR (estado = 'CREADO' AND acceso_confirmado IS DISTINCT FROM acceso_elegido))";

    private static final RowMapper<SalaMeet> SALA = SalaMeetJdbcAdapter::aSala;

    private final JdbcClient jdbcClient;

    SalaMeetJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<SalaMeet> porEvento(EventoId eventoId) {
        return jdbcClient.sql("SELECT " + COLUMNAS + " FROM renaser.eventos_meet WHERE evento_id = :evento ORDER BY fecha_ocurrencia")
                .param("evento", eventoId.value())
                .query(SALA)
                .list();
    }

    @Override
    public Map<EventoId, List<SalaMeet>> porEventos(Set<EventoId> eventoIds) {
        if (eventoIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = eventoIds.stream().map(EventoId::value).toList();
        List<SalaMeet> salas = jdbcClient.sql("SELECT " + COLUMNAS + " FROM renaser.eventos_meet WHERE evento_id IN (:ids) ORDER BY fecha_ocurrencia")
                .param("ids", ids)
                .query(SALA)
                .list();
        return salas.stream().collect(Collectors.groupingBy(SalaMeet::eventoId, HashMap::new, Collectors.toList()));
    }

    @Override
    public List<EventoId> eventosConTrabajoPendiente(Instant ahora, int limite) {
        return jdbcClient.sql("SELECT evento_id FROM renaser.eventos_meet "
                        + "WHERE proximo_intento IS NOT NULL AND proximo_intento <= :ahora AND intentos < :maximo AND " + CON_TRABAJO
                        + " GROUP BY evento_id ORDER BY MIN(proximo_intento) LIMIT :limite")
                .param("ahora", utc(ahora))
                .param("maximo", PoliticaDeReintentos.MAXIMO_DE_INTENTOS)
                .param("limite", limite)
                .query((rs, n) -> EventoId.of(rs.getObject("evento_id", UUID.class)))
                .list();
    }

    @Override
    public List<EventoId> eventosConSalaAutomatica(Instant ahora) {
        return jdbcClient.sql("""
                        SELECT e.id FROM renaser.eventos e
                        WHERE e.acceso_sala_meet IS NOT NULL AND e.estado = 'PUBLICADO'
                          AND (e.inicia_en >= :ayer
                               OR EXISTS (SELECT 1 FROM renaser.recurrencias_evento r
                                          WHERE r.evento_id = e.id AND (r.hasta IS NULL OR r.hasta >= :ayer)))
                        ORDER BY e.inicia_en
                        """)
                .param("ayer", utc(ahora.minusSeconds(86_400)))
                .query((rs, n) -> EventoId.of(rs.getObject("id", UUID.class)))
                .list();
    }

    @Override
    public SalaMeet guardar(SalaMeet sala) {
        jdbcClient.sql("""
                        INSERT INTO renaser.eventos_meet (%s)
                        VALUES (:id, :evento, :fecha, :idGoogle, :enlace, :estado, :elegido, :confirmado, :intentos, :proximo,
                                :error, :creada, :actualizada, 0, :grabacion, :transcripcion, :notas, :coEstado, :coMotivo)
                        ON CONFLICT (id) DO UPDATE SET fecha_ocurrencia = EXCLUDED.fecha_ocurrencia, enlace = EXCLUDED.enlace,
                            estado = EXCLUDED.estado, acceso_elegido = EXCLUDED.acceso_elegido,
                            acceso_confirmado = EXCLUDED.acceso_confirmado, intentos = EXCLUDED.intentos,
                            proximo_intento = EXCLUDED.proximo_intento, ultimo_error = EXCLUDED.ultimo_error,
                            actualizada_en = EXCLUDED.actualizada_en, version = eventos_meet.version + 1,
                            grabacion_confirmada = EXCLUDED.grabacion_confirmada,
                            transcripcion_confirmada = EXCLUDED.transcripcion_confirmada, notas_confirmadas = EXCLUDED.notas_confirmadas,
                            coanfitriones_estado = EXCLUDED.coanfitriones_estado, coanfitriones_motivo = EXCLUDED.coanfitriones_motivo
                        """.formatted(COLUMNAS))
                .param("id", sala.id())
                .param("evento", sala.eventoId().value())
                .param("fecha", Date.valueOf(sala.fechaOcurrencia()))
                .param("idGoogle", sala.idGoogle())
                .param("enlace", sala.enlace())
                .param("estado", sala.estado().name())
                .param("elegido", sala.accesoElegido().name())
                .param("confirmado", sala.accesoConfirmado() == null ? null : sala.accesoConfirmado().name())
                .param("intentos", sala.intentos())
                .param("proximo", utc(sala.proximoIntento()))
                .param("error", sala.ultimoError())
                .param("creada", utc(sala.creadaEn()))
                .param("actualizada", utc(sala.actualizadaEn()))
                .param("grabacion", sala.artefactos().grabacion())
                .param("transcripcion", sala.artefactos().transcripcion())
                .param("notas", sala.artefactos().notas())
                .param("coEstado", sala.coanfitriones() == null ? null : sala.coanfitriones().name())
                .param("coMotivo", sala.coanfitrionesMotivo())
                .update();
        return sala;
    }

    @Override
    @Transactional
    public void marcarParaSincronizar(EventoId eventoId, Instant ahora) {
        jdbcClient.sql("UPDATE renaser.eventos_meet SET estado = 'PENDIENTE_DE_SINCRONIZAR', intentos = 0, proximo_intento = :ahora, "
                        + "actualizada_en = :ahora, version = version + 1 WHERE evento_id = :evento AND estado = 'CREADO'")
                .param("ahora", utc(ahora))
                .param("evento", eventoId.value())
                .update();
    }

    @Override
    @Transactional
    public void reencolarLoPendiente(Instant ahora) {
        jdbcClient.sql("UPDATE renaser.eventos_meet SET intentos = 0, proximo_intento = :ahora, actualizada_en = :ahora, version = version + 1 "
                        + "WHERE " + CON_TRABAJO + " AND fecha_ocurrencia >= :desde")
                .param("ahora", utc(ahora))
                .param("desde", Date.valueOf(utc(ahora).toLocalDate().minusDays(1)))
                .update();
    }

    @Override
    @Transactional
    public void detenerLoPendiente(String motivo, Instant ahora) {
        jdbcClient.sql("UPDATE renaser.eventos_meet SET estado = CASE WHEN estado = 'PENDIENTE' THEN 'FALLIDO' ELSE estado END, "
                        + "proximo_intento = NULL, ultimo_error = :motivo, actualizada_en = :ahora, version = version + 1 WHERE " + CON_TRABAJO)
                .param("motivo", motivo)
                .param("ahora", utc(ahora))
                .update();
    }

    @Override
    @Transactional
    public int purgarLapidasAnterioresA(Instant limite) {
        return jdbcClient.sql("DELETE FROM renaser.eventos_meet WHERE estado = 'BORRADO' AND actualizada_en < :limite")
                .param("limite", utc(limite))
                .update();
    }

    // ─── Mapeo ──────────────────────────────────────────────────────────────────

    private static SalaMeet aSala(ResultSet rs, int fila) throws SQLException {
        String confirmado = rs.getString("acceso_confirmado");
        String coEstado = rs.getString("coanfitriones_estado");
        return SalaMeet.rehydrate(rs.getObject("id", UUID.class), EventoId.of(rs.getObject("evento_id", UUID.class)),
                rs.getDate("fecha_ocurrencia").toLocalDate(), rs.getString("id_google"), rs.getString("enlace"),
                EstadoSalaMeet.valueOf(rs.getString("estado")), AccesoSalaMeet.valueOf(rs.getString("acceso_elegido")),
                confirmado == null ? null : AccesoSalaMeet.valueOf(confirmado), rs.getInt("intentos"),
                instante(rs, "proximo_intento"), rs.getString("ultimo_error"), instante(rs, "creada_en"),
                instante(rs, "actualizada_en"), rs.getLong("version"),
                new ArtefactosDeLaSala(booleano(rs, "grabacion_confirmada"), booleano(rs, "transcripcion_confirmada"),
                        booleano(rs, "notas_confirmadas")),
                coEstado == null ? null : EstadoDeCoanfitriones.valueOf(coEstado), rs.getString("coanfitriones_motivo"));
    }

    private static Boolean booleano(ResultSet rs, String columna) throws SQLException {
        boolean valor = rs.getBoolean(columna);
        return rs.wasNull() ? null : valor;
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }
}

package com.renaser.os.calendar.infrastructure.adapter.out.persistence.confirmacion;

import com.renaser.os.calendar.application.ports.out.confirmacion.HistorialDeRespuestasPort;
import com.renaser.os.calendar.domain.model.asistencia.RespuestaAnterior;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * {@code historial_confirmaciones_evento} (V93, D-256) con {@link JdbcClient}: un INSERT y un SELECT de una
 * tabla append-only no justifican una entidad JPA. Los instantes viajan como {@link OffsetDateTime} en UTC,
 * que pgjdbc manda como {@code timestamptz} sin depender de la zona del proceso.
 */
@Component
class HistorialDeRespuestasJdbcAdapter implements HistorialDeRespuestasPort {

    private final JdbcClient jdbcClient;

    HistorialDeRespuestasJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void registrar(Confirmacion respuesta) {
        jdbcClient.sql("""
                        INSERT INTO renaser.historial_confirmaciones_evento
                               (evento_id, inicio_ocurrencia, usuario_id, estado, registrado_en)
                        VALUES (:evento, :slot, :usuario, CAST(:estado AS renaser.estado_confirmacion), :en)
                        """)
                .param("evento", respuesta.eventoId().value())
                .param("slot", utc(respuesta.inicioOcurrencia()))
                .param("usuario", respuesta.usuarioId().value())
                .param("estado", respuesta.estado().name())
                .param("en", utc(respuesta.actualizadoEn()))
                .update();
    }

    @Override
    public List<RespuestaAnterior> deOcurrencia(EventoId eventoId, Instant inicioOcurrencia) {
        return jdbcClient.sql("""
                        SELECT usuario_id, estado::text AS estado, registrado_en
                          FROM renaser.historial_confirmaciones_evento
                         WHERE evento_id = :evento AND inicio_ocurrencia = :slot
                         ORDER BY registrado_en, id
                        """)
                .param("evento", eventoId.value())
                .param("slot", utc(inicioOcurrencia))
                .query((rs, n) -> new RespuestaAnterior(UserId.of(rs.getObject("usuario_id", UUID.class)),
                        EstadoConfirmacion.valueOf(rs.getString("estado")),
                        rs.getObject("registrado_en", OffsetDateTime.class).toInstant()))
                .list();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}

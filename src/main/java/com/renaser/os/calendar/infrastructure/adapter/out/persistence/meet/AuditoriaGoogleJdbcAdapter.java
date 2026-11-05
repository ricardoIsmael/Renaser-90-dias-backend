package com.renaser.os.calendar.infrastructure.adapter.out.persistence.meet;

import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * {@code integracion_google_auditoria} (V169, spec 040, FR-016): append-only, 12 meses. Guarda qué se hizo, quién, sobre qué evento, cómo salió y
 * el código HTTP de Google: nunca un token, el cuerpo de lo que respondió Google ni el enlace de la sala.
 */
@Component
class AuditoriaGoogleJdbcAdapter implements AuditoriaGooglePort {

    private final JdbcClient jdbcClient;

    AuditoriaGoogleJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void registrar(RegistroDeAuditoriaGoogle r) {
        jdbcClient.sql("INSERT INTO renaser.integracion_google_auditoria (id, en, accion, actor_id, evento_id, exito, codigo_http, correlacion) "
                        + "VALUES (:id, :en, :accion, :actor, :evento, :exito, :codigo, :correlacion)")
                .param("id", r.id())
                .param("en", r.en().atOffset(ZoneOffset.UTC))
                .param("accion", r.accion().name())
                .param("actor", r.actor() == null ? null : r.actor().value())
                .param("evento", r.eventoId())
                .param("exito", r.exito())
                .param("codigo", r.codigoHttp() == null ? null : r.codigoHttp().shortValue())
                .param("correlacion", r.correlacion())
                .update();
    }

    @Override
    public List<RegistroDeAuditoriaGoogle> ultimos(int cuantos) {
        return jdbcClient.sql("SELECT id, en, accion, actor_id, evento_id, exito, codigo_http, correlacion "
                        + "FROM renaser.integracion_google_auditoria ORDER BY en DESC LIMIT :cuantos")
                .param("cuantos", cuantos)
                .query((rs, n) -> {
                    UUID actor = rs.getObject("actor_id", UUID.class);
                    short codigo = rs.getShort("codigo_http");
                    Integer codigoHttp = rs.wasNull() ? null : (int) codigo;
                    return new RegistroDeAuditoriaGoogle(rs.getObject("id", UUID.class),
                            rs.getObject("en", OffsetDateTime.class).toInstant(),
                            AccionDeAuditoriaGoogle.valueOf(rs.getString("accion")), actor == null ? null : UserId.of(actor),
                            rs.getObject("evento_id", UUID.class), rs.getBoolean("exito"), codigoHttp, rs.getString("correlacion"));
                })
                .list();
    }

    @Override
    public int purgarAnterioresA(Instant limite) {
        return jdbcClient.sql("DELETE FROM renaser.integracion_google_auditoria WHERE en < :limite")
                .param("limite", limite.atOffset(ZoneOffset.UTC))
                .update();
    }
}

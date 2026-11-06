package com.renaser.os.calendar.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.calendar.application.ports.out.audiencia.AudienciaElegidaPort;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.audiencia.AudienciaElegida;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.Invitado;
import com.renaser.os.calendar.domain.model.evento.audiencia.OrigenDelInvitado;
import com.renaser.os.calendar.domain.model.evento.audiencia.ReglaDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.audiencia.TipoDeParte;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code reglas_audiencia_evento} e {@code invitados_evento} (V173, spec 044) con {@link JdbcClient}. Guardar reemplaza todo lo del evento: la app
 * siempre manda la audiencia completa, nunca un cambio parcial.
 */
@Component
class AudienciaElegidaJdbcAdapter implements AudienciaElegidaPort {

    private final JdbcClient jdbc;

    AudienciaElegidaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AudienciaElegida de(EventoId evento) {
        return deEventos(List.of(evento)).getOrDefault(evento, AudienciaElegida.vacia());
    }

    @Override
    public Map<EventoId, AudienciaElegida> deEventos(Collection<EventoId> eventos) {
        if (eventos.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = eventos.stream().map(EventoId::value).collect(Collectors.toSet());
        Map<EventoId, List<ReglaDeAudiencia>> reglas = new HashMap<>();
        jdbc.sql("""
                        SELECT evento_id, tipo_parte, grupo_id, mentor_id, color, resuelto_en
                        FROM renaser.reglas_audiencia_evento WHERE evento_id IN (:ids) ORDER BY id
                        """)
                .param("ids", ids)
                .query((rs, n) -> {
                    UUID mentor = rs.getObject("mentor_id", UUID.class);
                    String color = rs.getString("color");
                    OffsetDateTime resuelto = rs.getObject("resuelto_en", OffsetDateTime.class);
                    reglas.computeIfAbsent(EventoId.of(rs.getObject("evento_id", UUID.class)), k -> new ArrayList<>())
                            .add(new ReglaDeAudiencia(TipoDeParte.valueOf(rs.getString("tipo_parte")),
                                    rs.getObject("grupo_id", UUID.class), mentor == null ? null : UserId.of(mentor),
                                    color == null ? null : ColorDeAudiencia.valueOf(color),
                                    resuelto == null ? null : resuelto.toInstant()));
                    return null;
                })
                .list();
        Map<EventoId, List<Invitado>> invitados = new HashMap<>();
        jdbc.sql("""
                        SELECT evento_id, participante_id, origen, color
                        FROM renaser.invitados_evento WHERE evento_id IN (:ids) ORDER BY creado_en, participante_id
                        """)
                .param("ids", ids)
                .query((rs, n) -> {
                    String color = rs.getString("color");
                    invitados.computeIfAbsent(EventoId.of(rs.getObject("evento_id", UUID.class)), k -> new ArrayList<>())
                            .add(new Invitado(UserId.of(rs.getObject("participante_id", UUID.class)),
                                    OrigenDelInvitado.valueOf(rs.getString("origen")),
                                    color == null ? null : ColorDeAudiencia.valueOf(color)));
                    return null;
                })
                .list();
        Map<EventoId, AudienciaElegida> resultado = new HashMap<>();
        for (EventoId evento : eventos) {
            List<ReglaDeAudiencia> r = reglas.getOrDefault(evento, List.of());
            List<Invitado> i = invitados.getOrDefault(evento, List.of());
            if (!r.isEmpty() || !i.isEmpty()) {
                resultado.put(evento, new AudienciaElegida(r, i));
            }
        }
        return resultado;
    }

    @Override
    public void reemplazar(EventoId evento, AudienciaElegida audiencia) {
        jdbc.sql("DELETE FROM renaser.reglas_audiencia_evento WHERE evento_id = :e").param("e", evento.value()).update();
        jdbc.sql("DELETE FROM renaser.invitados_evento WHERE evento_id = :e").param("e", evento.value()).update();
        for (ReglaDeAudiencia regla : audiencia.reglas()) {
            jdbc.sql("""
                            INSERT INTO renaser.reglas_audiencia_evento (evento_id, tipo_parte, grupo_id, mentor_id, color, resuelto_en)
                            VALUES (:e, :tipo, :grupo, :mentor, :color, :resuelto)
                            """)
                    .param("e", evento.value())
                    .param("tipo", regla.tipo().name())
                    .param("grupo", regla.grupoId())
                    .param("mentor", regla.mentorId() == null ? null : regla.mentorId().value())
                    .param("color", regla.color() == null ? null : regla.color().name())
                    .param("resuelto", regla.resueltoEn() == null ? null : regla.resueltoEn().atOffset(ZoneOffset.UTC))
                    .update();
        }
        for (Invitado invitado : audiencia.invitados()) {
            jdbc.sql("""
                            INSERT INTO renaser.invitados_evento (evento_id, participante_id, origen, color)
                            VALUES (:e, :p, :origen, :color) ON CONFLICT DO NOTHING
                            """)
                    .param("e", evento.value())
                    .param("p", invitado.persona().value())
                    .param("origen", invitado.origen().name())
                    .param("color", invitado.color() == null ? null : invitado.color().name())
                    .update();
        }
    }
}

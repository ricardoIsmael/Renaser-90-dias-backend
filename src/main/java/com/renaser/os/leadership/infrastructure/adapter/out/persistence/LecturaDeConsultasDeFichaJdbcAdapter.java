package com.renaser.os.leadership.infrastructure.adapter.out.persistence;

import com.renaser.os.leadership.application.ports.out.LeerConsultasDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lee {@code consultas_de_ficha} (V109) con {@link JdbcClient}. Solo el líder de mentores: el resumen al mentor no
 * cuenta lo que miran Administración y Alquimista (FR-017). El índice {@code (mentor_id, consultada_en)} cubre el
 * filtro.
 */
@Component
class LecturaDeConsultasDeFichaJdbcAdapter implements LeerConsultasDeFichaPort {

    private static final String DEL_LIDER = """
            SELECT id, consultor_id, rol_consultor, persona_id, grupo_id, mentor_id, semana_desde, consultada_en
              FROM renaser.consultas_de_ficha
             WHERE mentor_id IN (:mentores)
               AND rol_consultor = 'MENTOR_LEAD'
               AND consultada_en >= :desde AND consultada_en < :hasta
             ORDER BY consultada_en, id
            """;

    private final JdbcClient jdbcClient;

    LecturaDeConsultasDeFichaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<ConsultaDeFicha> delLiderEntre(Collection<UserId> mentores, Instant desde, Instant hasta) {
        if (mentores.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql(DEL_LIDER)
                .param("mentores", mentores.stream().map(UserId::value).toList())
                .param("desde", Timestamp.from(desde))
                .param("hasta", Timestamp.from(hasta))
                .query((fila, n) -> new ConsultaDeFicha(fila.getObject("id", UUID.class),
                        UserId.of(fila.getObject("consultor_id", UUID.class)),
                        UserRole.valueOf(fila.getString("rol_consultor")),
                        UserId.of(fila.getObject("persona_id", UUID.class)), fila.getObject("grupo_id", UUID.class),
                        UserId.of(fila.getObject("mentor_id", UUID.class)),
                        fila.getDate("semana_desde").toLocalDate(), fila.getTimestamp("consultada_en").toInstant()))
                .list();
    }
}

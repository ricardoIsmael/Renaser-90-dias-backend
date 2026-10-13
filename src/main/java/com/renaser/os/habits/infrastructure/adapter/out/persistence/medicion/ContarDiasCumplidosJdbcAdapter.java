package com.renaser.os.habits.infrastructure.adapter.out.persistence.medicion;

import com.renaser.os.habits.application.ports.out.medicion.ContarDiasCumplidosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ContarDiasCumplidosPort} con {@link JdbcClient}: una agregación para el ranking, como
 * {@code SumarMedicionesJdbcAdapter} (regla 04). La UNIQUE {@code (participante_id, habito_id, fecha_ejecucion)} sirve
 * de índice y garantiza un registro por día.
 */
@Component
class ContarDiasCumplidosJdbcAdapter implements ContarDiasCumplidosPort {

    private static final String SQL = """
            SELECT r.participante_id AS participante_id, COUNT(*) AS dias
            FROM renaser.registros_habito r
            JOIN renaser.habitos h ON h.id = r.habito_id
            WHERE h.clave_sistema = :clave
              AND r.participante_id IN (:participantes)
              AND r.fecha_ejecucion <= :hasta
              AND r.estado = 'COMPLETADO'
            GROUP BY r.participante_id
            """;

    private final JdbcClient jdbcClient;

    ContarDiasCumplidosJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<UserId, Integer> diasPorParticipante(Collection<UserId> participantes, String claveSistema,
                                                    LocalDate hasta) {
        if (participantes.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = participantes.stream().map(UserId::value).distinct().toList();
        Map<UserId, Integer> dias = new HashMap<>();
        jdbcClient.sql(SQL).param("clave", claveSistema).param("participantes", ids).param("hasta", hasta)
                .query((rs, fila) -> Map.entry(UserId.of(rs.getObject("participante_id", UUID.class)),
                        rs.getInt("dias")))
                .list()
                .forEach(e -> dias.put(e.getKey(), e.getValue()));
        return dias;
    }
}

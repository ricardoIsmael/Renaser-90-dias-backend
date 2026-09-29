package com.renaser.os.habits.infrastructure.adapter.out.persistence.medicion;

import com.renaser.os.habits.application.ports.out.medicion.SumarMedicionesPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link SumarMedicionesPort} con {@link JdbcClient}: es una agregación para el ranking, justo el caso
 * que la regla 04 reserva para JDBC. Las dos tablas son de {@code habits} (D-41).
 *
 * <p>La UNIQUE {@code (participante_id, habito_id, fecha_ejecucion)} de {@code registros_habito} sirve
 * de índice: por cada participante es un rango sobre su hábito, no un barrido de la tabla.
 */
@Component
class SumarMedicionesJdbcAdapter implements SumarMedicionesPort {

    private static final String SQL = """
            SELECT r.participante_id AS participante_id, SUM(r.valor_medido) AS total
            FROM renaser.registros_habito r
            JOIN renaser.habitos h ON h.id = r.habito_id
            WHERE h.clave_sistema = :clave
              AND r.participante_id IN (:participantes)
              AND r.fecha_ejecucion <= :hasta
              AND r.estado = 'COMPLETADO'
              AND r.valor_medido IS NOT NULL
            GROUP BY r.participante_id
            """;

    private final JdbcClient jdbcClient;

    SumarMedicionesJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<UserId, BigDecimal> sumaPorParticipante(Collection<UserId> participantes, String claveSistema,
                                                       LocalDate hasta) {
        if (participantes.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = participantes.stream().map(UserId::value).distinct().toList();
        Map<UserId, BigDecimal> sumas = new HashMap<>();
        jdbcClient.sql(SQL).param("clave", claveSistema).param("participantes", ids).param("hasta", hasta)
                .query((rs, fila) -> Map.entry(UserId.of(rs.getObject("participante_id", UUID.class)),
                        rs.getBigDecimal("total")))
                .list()
                .forEach(e -> sumas.put(e.getKey(), e.getValue()));
        return sumas;
    }
}

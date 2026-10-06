package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.application.ports.out.registro.ConsultarJornadasGeneradasPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * {@link ConsultarJornadasGeneradasPort} con {@link JdbcClient}: una consulta por lote, sobre una tabla de
 * {@code habits} (D-41). Usa {@code registros_dia_idx}; sin migracion.
 */
@Component
class JornadasGeneradasJdbcAdapter implements ConsultarJornadasGeneradasPort {

    private static final String SQL = """
            SELECT DISTINCT participante_id
            FROM renaser.registros_habito
            WHERE fecha_ejecucion = :fecha
              AND participante_id IN (:participantes)
            """;

    private final JdbcClient jdbcClient;

    JornadasGeneradasJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UserId> conRegistrosEn(Collection<UserId> participantes, LocalDate fecha) {
        if (participantes.isEmpty()) {
            return Set.of();
        }
        Set<UserId> conRegistros = new HashSet<>();
        jdbcClient.sql(SQL)
                .param("fecha", fecha)
                .param("participantes", participantes.stream().map(UserId::value).toList())
                .query((rs, fila) -> rs.getObject("participante_id", UUID.class))
                .list()
                .forEach(id -> conRegistros.add(UserId.of(id)));
        return conRegistros;
    }
}

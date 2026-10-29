package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder.RegistroDeHabito;
import com.renaser.os.habits.application.ports.out.registro.LeerRegistrosDelRangoPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@link LeerRegistrosDelRangoPort} con {@link JdbcClient}: una lectura de {@code registros_habito} de una persona
 * por rango de fechas, resuelta por {@code registros_dia_idx}. El estado se traduce con {@code valueOf} sobre el
 * nombre, como {@code DiasProgramadosJdbcAdapter}: si la base ganara un estado nuevo, revienta en vez de adivinar.
 */
@Component
class RegistrosDelRangoJdbcAdapter implements LeerRegistrosDelRangoPort {

    private static final String SQL = """
            SELECT habito_id, fecha_ejecucion, estado::text AS estado, es_opcional, completado_en
            FROM renaser.registros_habito
            WHERE participante_id = :participante
              AND fecha_ejecucion BETWEEN :desde AND :hasta
            ORDER BY fecha_ejecucion, creado_en
            """;

    private final JdbcClient jdbcClient;

    RegistrosDelRangoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<RegistroDeHabito> deParticipanteEntre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        return jdbcClient.sql(SQL)
                .param("participante", participanteId.value()).param("desde", desde).param("hasta", hasta)
                .query((rs, fila) -> {
                    Timestamp completadoEn = rs.getTimestamp("completado_en");
                    return new RegistroDeHabito(rs.getObject("habito_id", UUID.class),
                            rs.getObject("fecha_ejecucion", LocalDate.class),
                            EstadoObligacion.valueOf(rs.getString("estado")), rs.getBoolean("es_opcional"),
                            completadoEn == null ? null : completadoEn.toInstant());
                })
                .list();
    }
}

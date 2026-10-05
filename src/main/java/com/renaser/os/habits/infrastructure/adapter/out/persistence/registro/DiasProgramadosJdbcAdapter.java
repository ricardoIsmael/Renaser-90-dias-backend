package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.application.ports.out.registro.ConsultarDiasProgramadosPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.DiaProgramado;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@link ConsultarDiasProgramadosPort} con {@link JdbcClient}: es una lectura del camino caliente
 * ({@code GET /habit-tracks/today}), el caso que la regla 04 reserva para JDBC, y la tabla es de
 * {@code habits} (D-41).
 *
 * <p>Proyecta solo las cuatro columnas que la racha mira. La UNIQUE
 * {@code (participante_id, habito_id, fecha_ejecucion)} de {@code registros_habito} sirve de indice:
 * por cada habito es un rango de fechas, no un barrido de la tabla. Sin migracion.
 *
 * <p>El estado se traduce con {@code valueOf} sobre el nombre: el enum de la base y
 * {@link EstadoRegistro} se llaman igual valor por valor, y si la base ganara un estado nuevo esto
 * revienta en vez de adivinar que significa para la racha.
 */
@Component
class DiasProgramadosJdbcAdapter implements ConsultarDiasProgramadosPort {

    private static final String SQL = """
            SELECT habito_id, fecha_ejecucion, estado::text AS estado, es_opcional
            FROM renaser.registros_habito
            WHERE participante_id = :participante
              AND habito_id IN (:habitos)
              AND fecha_ejecucion BETWEEN :desde AND :hasta
            """;

    private final JdbcClient jdbcClient;

    DiasProgramadosJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<HabitoId, List<DiaProgramado>> deHabitosEntre(UserId participanteId, Collection<HabitoId> habitos,
                                                             LocalDate desde, LocalDate hasta) {
        if (habitos.isEmpty() || hasta.isBefore(desde)) {
            return Map.of();
        }
        List<UUID> ids = habitos.stream().map(HabitoId::value).distinct().toList();
        Map<HabitoId, List<DiaProgramado>> porHabito = new HashMap<>();
        jdbcClient.sql(SQL).param("participante", participanteId.value()).param("habitos", ids)
                .param("desde", desde).param("hasta", hasta)
                .query((rs, fila) -> Map.entry(HabitoId.of(rs.getObject("habito_id", UUID.class)),
                        new DiaProgramado(rs.getObject("fecha_ejecucion", LocalDate.class),
                                EstadoRegistro.valueOf(rs.getString("estado")), rs.getBoolean("es_opcional"))))
                .list()
                .forEach(e -> porHabito.computeIfAbsent(e.getKey(), h -> new ArrayList<>()).add(e.getValue()));
        return porHabito;
    }
}

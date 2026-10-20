package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.application.ports.out.registro.ContarCumplimientoPorHabitoPort;
import com.renaser.os.habits.domain.model.registro.CumplimientoDeUnHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link ContarCumplimientoPorHabitoPort} con {@link JdbcClient}: una agregacion de lectura sobre
 * {@code registros_habito}, que resuelve la UNIQUE {@code (participante_id, habito_id, fecha_ejecucion)} y el
 * indice {@code registros_dia_idx} ({@code participante_id, fecha_ejecucion}) para un solo participante.
 *
 * <p>El nombre es el que ve la persona: el que le puso ella ({@code renombres_habito.titulo_personal}) o, si no lo
 * renombro, el del catalogo. Las tres cuentas son crudas; que dias "contaban" no se decide aca sino en
 * {@code ConteoDiarioHabitos.calificables} (mismo criterio que {@code ContarRegistrosDiariosHabitsPersistenceAdapter}:
 * opcional sin completar = {@code es_opcional} y estado distinto de COMPLETADO).
 */
@Component
class CumplimientoPorHabitoJdbcAdapter implements ContarCumplimientoPorHabitoPort {

    private static final String SQL = """
            SELECT COALESCE(ren.titulo_personal, h.titulo) AS nombre,
                   COUNT(*) AS total_registros,
                   COUNT(*) FILTER (WHERE r.estado = CAST('COMPLETADO' AS renaser.estado_registro)) AS completados,
                   COUNT(*) FILTER (WHERE r.es_opcional
                                      AND r.estado <> CAST('COMPLETADO' AS renaser.estado_registro))
                       AS opcionales_no_completados
            FROM renaser.registros_habito r
            JOIN renaser.habitos h ON h.id = r.habito_id
            LEFT JOIN renaser.renombres_habito ren
                   ON ren.habito_id = r.habito_id AND ren.participante_id = r.participante_id
            WHERE r.participante_id = :participante
              AND r.fecha_ejecucion BETWEEN :desde AND :hasta
            GROUP BY r.habito_id, h.titulo, ren.titulo_personal
            ORDER BY nombre
            """;

    private final JdbcClient jdbcClient;

    CumplimientoPorHabitoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<CumplimientoDeUnHabito> deParticipanteEntre(UserId participante, LocalDate desde, LocalDate hasta) {
        return jdbcClient.sql(SQL)
                .param("participante", participante.value()).param("desde", desde).param("hasta", hasta)
                .query((rs, fila) -> new CumplimientoDeUnHabito(rs.getString("nombre"),
                        rs.getInt("total_registros"), rs.getInt("completados"),
                        rs.getInt("opcionales_no_completados")))
                .list();
    }
}

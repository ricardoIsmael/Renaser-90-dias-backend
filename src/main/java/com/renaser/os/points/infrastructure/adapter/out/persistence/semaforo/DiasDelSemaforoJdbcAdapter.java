package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo;

import com.renaser.os.points.application.ports.out.semaforo.CargarDiasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.GuardarDiasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * {@code semaforo_dias} con {@link JdbcClient}: el upsert necesita {@code ON CONFLICT ... WHERE ...
 * IS DISTINCT FROM} para no reescribir un día que no cambió (el barrido recalcula cada hora los días
 * de la semana abierta), y eso no se expresa con Spring Data. Solo toca su propia tabla.
 */
@Component
class DiasDelSemaforoJdbcAdapter implements CargarDiasDelSemaforoPort, GuardarDiasDelSemaforoPort {

    private static final String GUARDAR = """
            INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos,
                                               objetivos_programados, objetivos_cumplidos, calculado_en)
            VALUES (:participante, :fecha, :habitosProgramados, :habitosCumplidos,
                    :objetivosProgramados, :objetivosCumplidos, :calculadoEn)
            ON CONFLICT (participante_id, fecha) DO UPDATE
               SET habitos_programados = EXCLUDED.habitos_programados,
                   habitos_cumplidos = EXCLUDED.habitos_cumplidos,
                   objetivos_programados = EXCLUDED.objetivos_programados,
                   objetivos_cumplidos = EXCLUDED.objetivos_cumplidos,
                   calculado_en = EXCLUDED.calculado_en
             WHERE (renaser.semaforo_dias.habitos_programados, renaser.semaforo_dias.habitos_cumplidos,
                    renaser.semaforo_dias.objetivos_programados, renaser.semaforo_dias.objetivos_cumplidos)
                   IS DISTINCT FROM
                   (EXCLUDED.habitos_programados, EXCLUDED.habitos_cumplidos,
                    EXCLUDED.objetivos_programados, EXCLUDED.objetivos_cumplidos)
            """;

    private static final String ENTRE = """
            SELECT participante_id, fecha, habitos_programados, habitos_cumplidos,
                   objetivos_programados, objetivos_cumplidos
            FROM renaser.semaforo_dias
            WHERE participante_id IN (:participantes) AND fecha BETWEEN :desde AND :hasta
            """;

    private static final String ULTIMO_CALCULO =
            "SELECT max(calculado_en) FROM renaser.semaforo_dias WHERE participante_id = :participante";

    private final JdbcClient jdbcClient;

    DiasDelSemaforoJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public int guardar(UserId participante, Collection<CumplimientoDelDia> dias, Instant calculadoEn) {
        Timestamp momento = Timestamp.from(calculadoEn);
        int cambiados = 0;
        for (CumplimientoDelDia dia : dias) {
            cambiados += jdbcClient.sql(GUARDAR)
                    .param("participante", participante.value())
                    .param("fecha", dia.fecha())
                    .param("habitosProgramados", dia.habitosProgramados())
                    .param("habitosCumplidos", dia.habitosCumplidos())
                    .param("objetivosProgramados", dia.objetivosProgramados())
                    .param("objetivosCumplidos", dia.objetivosCumplidos())
                    .param("calculadoEn", momento)
                    .update();
        }
        return cambiados;
    }

    @Override
    public Map<UserId, Map<LocalDate, CumplimientoDelDia>> entre(Collection<UserId> participantes, LocalDate desde,
                                                                 LocalDate hasta) {
        Map<UserId, Map<LocalDate, CumplimientoDelDia>> resultado = new LinkedHashMap<>();
        if (participantes.isEmpty()) {
            return resultado;
        }
        jdbcClient.sql(ENTRE)
                .param("participantes", participantes.stream().map(UserId::value).distinct().toList())
                .param("desde", desde)
                .param("hasta", hasta)
                .query((rs, n) -> {
                    UserId id = UserId.of(rs.getObject("participante_id", UUID.class));
                    CumplimientoDelDia dia = new CumplimientoDelDia(rs.getObject("fecha", LocalDate.class),
                            rs.getInt("habitos_programados"), rs.getInt("habitos_cumplidos"),
                            rs.getInt("objetivos_programados"), rs.getInt("objetivos_cumplidos"));
                    resultado.computeIfAbsent(id, k -> new TreeMap<>()).put(dia.fecha(), dia);
                    return dia;
                })
                .list();
        return resultado;
    }

    @Override
    public Optional<Instant> ultimoCalculoDe(UserId participante) {
        return jdbcClient.sql(ULTIMO_CALCULO)
                .param("participante", participante.value())
                .query((rs, n) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant))
                .single();
    }
}

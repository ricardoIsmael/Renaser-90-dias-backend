package com.renaser.os.habits.infrastructure.adapter.out.persistence.sinred;

import com.renaser.os.habits.application.ports.out.sinred.UltimaEdicionDelPlanDeHabitosPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * La hora lógica de las cuatro tablas del plan de hábitos (spec 048, fase 3): {@code preferencias_horario}, {@code horario_semanal_habito},
 * {@code horarios_habito_por_fecha} y {@code desbloqueos_habito}. Todas son tablas de {@code habits}; solo se lee un {@code max(actualizado_en)}.
 */
@Component
class UltimaEdicionDelPlanDeHabitosJdbcAdapter implements UltimaEdicionDelPlanDeHabitosPort {

    private final JdbcClient jdbc;

    UltimaEdicionDelPlanDeHabitosJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Instant> deLaPreferencia(UserId participanteId, HabitoId habitoId) {
        return ultima(jdbc.sql("SELECT max(actualizado_en) FROM renaser.preferencias_horario WHERE participante_id = :p AND habito_id = :h")
                .param("p", participanteId.value()).param("h", habitoId.value()).query(Timestamp.class).optional());
    }

    @Override
    public Optional<Instant> delDiaDeLaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana) {
        return ultima(jdbc.sql("SELECT max(actualizado_en) FROM renaser.horario_semanal_habito "
                        + "WHERE participante_id = :p AND habito_id = :h AND dia_semana = :d")
                .param("p", participanteId.value()).param("h", habitoId.value()).param("d", (short) diaSemana.getValue())
                .query(Timestamp.class).optional());
    }

    @Override
    public Optional<Instant> delasFechas(UserId participanteId, HabitoId habitoId, LocalDate desde, LocalDate hasta) {
        return ultima(jdbc.sql("SELECT max(actualizado_en) FROM renaser.horarios_habito_por_fecha WHERE participante_id = :p "
                        + "AND (CAST(:h AS uuid) IS NULL OR habito_id = CAST(:h AS uuid)) AND fecha BETWEEN :desde AND :hasta")
                .param("p", participanteId.value()).param("h", habitoId == null ? null : habitoId.value())
                .param("desde", desde).param("hasta", hasta).query(Timestamp.class).optional());
    }

    @Override
    public Optional<Instant> delDesbloqueo(UserId participanteId, HabitoId habitoId) {
        return ultima(jdbc.sql("SELECT max(actualizado_en) FROM renaser.desbloqueos_habito WHERE participante_id = :p AND habito_id = :h")
                .param("p", participanteId.value()).param("h", habitoId.value()).query(Timestamp.class).optional());
    }

    /** {@code max()} sin filas devuelve una fila con {@code NULL}: se lee como «no hay». */
    private static Optional<Instant> ultima(Optional<Timestamp> maximo) {
        return maximo.map(Timestamp::toInstant);
    }
}

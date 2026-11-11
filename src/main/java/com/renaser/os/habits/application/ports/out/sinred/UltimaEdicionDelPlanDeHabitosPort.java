package com.renaser.os.habits.application.ports.out.sinred;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Spec 048 (FR-019, D13): la hora lógica ({@code actualizado_en}) de las filas del plan de hábitos que un envío hecho sin red toca, para
 * saber si cambiaron DESPUÉS de lo que hizo la persona (entonces gana el servidor). Cada método devuelve la más reciente de las filas que
 * toca, o vacío si no hay ninguna (lo que no existe no choca: R2, no hay lápidas). Solo lectura.
 */
public interface UltimaEdicionDelPlanDeHabitosPort {

    /** {@code preferencias_horario(participante, hábito)}: la hora, el aviso y la alarma generales del hábito. */
    Optional<Instant> deLaPreferencia(UserId participanteId, HabitoId habitoId);

    /** {@code horario_semanal_habito(participante, hábito, día)}: la hora, el aviso y la alarma de ese día de la semana. */
    Optional<Instant> delDiaDeLaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana);

    /**
     * {@code horarios_habito_por_fecha}: las fechas propias entre {@code desde} y {@code hasta} (inclusive) de ese hábito, o de todos si
     * {@code habitoId} es {@code null} (repetir o deshacer una semana toca el plan de todos los hábitos).
     */
    Optional<Instant> delasFechas(UserId participanteId, HabitoId habitoId, LocalDate desde, LocalDate hasta);

    /** {@code desbloqueos_habito(participante, hábito)}: que el hábito esté en el plan, su día de inicio y su pausa. */
    Optional<Instant> delDesbloqueo(UserId participanteId, HabitoId habitoId);
}

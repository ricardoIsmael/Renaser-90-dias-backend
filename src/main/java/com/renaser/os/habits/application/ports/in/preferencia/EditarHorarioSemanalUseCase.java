package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * "Los lunes a las 5 y los martes a las 4" (V39).
 *
 * <p>Separado de {@link EditarPreferenciaHorarioUseCase}, que fija UNA hora para toda la semana, y
 * de la excepcion por fecha, que vale para un dia y no se repite. Los tres conviven y la
 * precedencia va de mas especifico a mas general: fecha exacta &gt; dia de semana &gt; cambio
 * general vigente &gt; preferencia general &gt; catalogo.
 *
 * <p><b>Fijar la hora SI consume el cupo semanal</b> de {@code CuotaEdicionHorario}, con la proxima
 * ocurrencia de ese dia como fecha efectiva.
 *
 * <p>Este javadoc decia lo contrario hasta el 2026-09-07: que no cobraba cupo porque "un patron
 * semanal no tiene fecha efectiva". Era falso — la tiene, y es la proxima vez que caiga ese dia —,
 * y el efecto era un agujero: para esquivar el limite de 3 habitos por semana alcanzaba con pedir
 * el cambio por dia de semana en vez de por horario general.
 *
 * <p>{@link #apagar} sigue siendo gratis, y eso si es deliberado: apagar un dia es hermano de la
 * pausa de {@code habit-unlocks}, que nunca cobro cupo, y no es "reacomodar el horario".
 */
public interface EditarHorarioSemanalUseCase {

    /** Fija la hora de ese dia. Si ya habia una, la reemplaza. */
    void fijar(UserId actorId, HabitoId habitoId, DayOfWeek diaSemana, LocalTime horaDisparo, LocalTime horaLimite);

    /**
     * Apaga el habito ESE dia de la semana, todas las semanas (V40).
     *
     * <p>Un habito con {@code habitos.desactivable = false} no se puede apagar — misma regla y
     * mismo motivo que la pausa de `habit-unlocks`: si los obligatorios se pudieran sacar un dia,
     * "obligatorio" no querria decir nada. Es la acotacion a la objecion de V31, que rechazo los
     * patrones semanales porque "crean un agujero PERMANENTE y silencioso en un programa de 90
     * dias".
     */
    void apagar(UserId actorId, HabitoId habitoId, DayOfWeek diaSemana);

    /** Quita la hora propia o el apagado de ese dia: vuelve al horario general. Idempotente. */
    void quitar(UserId actorId, HabitoId habitoId, DayOfWeek diaSemana);

    /** Los SIETE dias ya resueltos, para que la pantalla no reimplemente la precedencia. */
    List<DiaDeLaSemana> consultar(UserId actorId, HabitoId habitoId);

    /**
     * @param propio {@code true} si ese dia tiene hora propia; {@code false} si hereda la general.
     */
    record DiaDeLaSemana(DayOfWeek diaSemana, LocalTime horaDisparo, LocalTime horaLimite, boolean propio,
                          boolean activo) {
    }
}

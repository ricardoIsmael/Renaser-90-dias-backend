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
 * <p><b>Hoy NO consume el cupo semanal de {@code CuotaEdicionHorario}</b>, y es una decision
 * consciente, no un olvido: la cuota se mide contra una FECHA EFECTIVA ("este cambio rige desde el
 * dia X") y un patron semanal no tiene una. Inventarle una seria elegir una semantica de cuota sin
 * que nadie la haya pedido. Queda anotado como agujero conocido: quien quiera esquivar el cupo de
 * 3 cambios por semana puede hacerlo por esta via.
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

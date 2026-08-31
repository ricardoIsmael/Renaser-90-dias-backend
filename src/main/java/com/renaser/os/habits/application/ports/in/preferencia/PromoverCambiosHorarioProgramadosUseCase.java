package com.renaser.os.habits.application.ports.in.preferencia;

import java.time.LocalDate;

/**
 * Barrido nocturno que hace que un cambio de horario diferido (§12.1, "no se improvisa el dia")
 * llegue a regir de verdad: hasta ahora la fila de {@code cambios_horario_pendientes} se
 * escribia y nadie la leia nunca (E-53).
 *
 * <p>Va en un job y no en el camino de lectura a proposito: el RNF principal del proyecto es
 * latencia, y {@code TracksDelDiaProyeccionService} — que arma el dia del aprendiz — es hot
 * path; sumarle una consulta de pendientes por habito por request seria pagar en cada lectura
 * lo que cuesta una sola vez por noche.
 */
public interface PromoverCambiosHorarioProgramadosUseCase {

    /**
     * Aplica en {@code preferencias_horario} todos los cambios cuya fecha efectiva ya llego
     * ({@code fecha_efectiva <= fecha}), los borra de pendientes y los deja registrados en
     * {@code historial_cambios_horario}.
     *
     * <p><b>Idempotente:</b> borrar el pendiente en la misma transaccion en que se registra el
     * historial es lo que hace que correrlo dos veces el mismo dia no duplique nada — la segunda
     * pasada no encuentra pendientes que promover.
     *
     * @return cuantos cambios se promovieron
     */
    int promoverLosQueRigenEn(LocalDate fecha);
}

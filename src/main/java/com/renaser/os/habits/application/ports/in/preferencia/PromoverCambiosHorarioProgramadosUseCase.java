package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * Barrido que hace que un cambio de horario diferido (§12.1, "no se improvisa el dia")
 * llegue a regir de verdad: hasta ahora la fila de {@code cambios_horario_pendientes} se
 * escribia y nadie la leia nunca (E-53).
 *
 * <p>Va en un job y no en el camino de lectura a proposito: el RNF principal del proyecto es
 * latencia, y {@code TracksDelDiaProyeccionService} — que arma el dia del aprendiz — es hot
 * path; sumarle una consulta de pendientes por habito por request seria pagar en cada lectura
 * lo que cuesta una sola vez por dia.
 *
 * <p><b>Cuando rige un cambio lo decide el dia LOCAL de cada participante (E-557, regla 02 §1):</b>
 * {@code fecha_efectiva} es una fecha en la zona de la persona, asi que se compara contra SU hoy y
 * no contra la fecha UTC del servidor. {@code promoverLosQueRigenEn(LocalDate)}, que recibia esa fecha
 * UTC, dejo de existir.
 *
 * <p><b>Idempotentes:</b> el pendiente se borra en la misma transaccion en que se registra el
 * historial, y solo quien lo borro de verdad lo registra, asi que correrlos dos veces el mismo dia
 * (o dos instancias a la vez) no duplica nada: la segunda pasada no encuentra pendientes.
 */
public interface PromoverCambiosHorarioProgramadosUseCase {

    /**
     * Aplica en {@code preferencias_horario} todos los cambios cuya fecha efectiva ya llego PARA SU
     * PARTICIPANTE ({@code fecha_efectiva <= hoy en su zona}), los borra de pendientes y los deja
     * registrados en {@code historial_cambios_horario}. Corre cada hora; una hora en que a nadie le
     * llego el dia no hace nada.
     *
     * @return cuantos cambios se promovieron
     */
    int promoverLosQueYaRigen();

    /**
     * Lo mismo para UN participante y su hoy ya calculado en su zona. Lo llama quien le va a armar el
     * dia ({@code GeneracionDeJornadasService}): el orden "primero rige el horario nuevo, despues se
     * genera el dia" queda garantizado por participante y no por la hora de dos crons.
     *
     * @return cuantos cambios de esa persona se promovieron
     */
    int promoverLosDe(UserId participanteId, LocalDate hoyEnSuZona);
}

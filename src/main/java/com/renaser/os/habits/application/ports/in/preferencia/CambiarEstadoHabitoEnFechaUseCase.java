package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * El interruptor de UN dia: "el jueves no". Separado de {@link EditarPreferenciaHorarioUseCase} a
 * proposito, aunque las dos escriban en `horarios_habito_por_fecha`.
 *
 * <p><b>Por que no es un campo mas del PATCH de horario.</b> Son dos operaciones con reglas
 * distintas, y meterlas juntas obligaria a que cada una se saltee la mitad de la otra:
 * <ul>
 *   <li><b>Cuota.</b> Cambiar la hora consume el cupo semanal de `CuotaEdicionHorario`; bajar el
 *       interruptor no, porque es el hermano de la pausa de `habit-unlocks`, que nunca cobro cupo.
 *   <li><b>Dia en curso.</b> Mover la hora de HOY esta prohibido (el dia no se reacomoda). Apagar
 *       HOY tiene que poder hacerse: es lo que una pantalla del dia necesita.
 *   <li><b>Hora obligatoria.</b> El PATCH exige `triggerTime`; apagar un dia no tiene ninguna.
 * </ul>
 */
public interface CambiarEstadoHabitoEnFechaUseCase {

    /**
     * @param activo {@code false} escribe la excepcion que apaga ese dia; {@code true} la BORRA, que
     *               es lo que "volver a lo normal" significa — no una fila que diga que si.
     */
    void cambiarEstadoEnFecha(UserId actorId, HabitoId habitoId, LocalDate fecha, boolean activo);
}

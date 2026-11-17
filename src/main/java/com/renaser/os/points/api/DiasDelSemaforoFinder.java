package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * El color y el porcentaje de cada día de una persona en un rango, para pintar la semana y el mes de su ficha (spec 055).
 *
 * <p><b>La misma regla del semáforo, no otra.</b> Lee los días que el barrido horario ya guardó en {@code semaforo_dias} y los
 * resuelve con la misma regla de {@link ColorSemaforo} que la ventana vigente y la semana: un día medido trae porcentaje
 * y color; uno pausado, con la cuenta suspendida, sin nada programado o que todavía no cierra trae su estado y ningún
 * porcentaje. Ninguna lectura recalcula. No autoriza: quien llama ya estableció que el actor puede ver a esa persona.
 */
public interface DiasDelSemaforoFinder {

    /**
     * @param desde primer día del rango, en la fecha LOCAL de la persona
     * @param hasta último día del rango, ídem; puede ser hoy o un día por venir (sale {@code PENDIENTE})
     * @return un {@link DiaDelSemaforo} por fecha, del más viejo al más nuevo; vacío si el semáforo no la mide (sin
     *         programa activado)
     */
    Optional<List<DiaDelSemaforo>> diasDe(UserId participante, LocalDate desde, LocalDate hasta);
}

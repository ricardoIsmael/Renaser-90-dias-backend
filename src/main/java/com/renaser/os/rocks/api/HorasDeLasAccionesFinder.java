package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Las acciones del día de una persona entre dos fechas con su hora y el instante en que se cumplieron: lo que
 * {@code mentoring} necesita para calcular los huecos de horario (spec 015). Una acción es lo que la app llama «Tus
 * acciones»: la roca diaria.
 *
 * <p>Es otro contrato que {@link AccionesDelDiaFinder} porque pregunta otra cosa: aquel suma la evidencia para la ficha
 * del líder (y por eso toca {@code evidence}); este solo lee las horas, sin título ni evidencia.
 *
 * <p><b>No autoriza</b>, igual que los otros finders de este paquete: quien llama ya estableció que el actor puede ver a
 * esa persona.
 */
public interface HorasDeLasAccionesFinder {

    /**
     * @param desde inclusivo, fecha local de la persona ya resuelta por quien llama
     * @param hasta inclusivo
     * @return todas las acciones del rango, con o sin hora, ordenadas por fecha y posición; nunca null
     */
    List<HoraDeUnaAccion> entre(UserId aprendizId, LocalDate desde, LocalDate hasta);

    /**
     * @param horaInicio  hora local; {@code null} si no se fijó (una acción sin hora no ocupa un tramo)
     * @param horaFin     hora local; {@code null} si no se fijó. Puede ser anterior a la de inicio: ninguna validación
     *                    lo impide, y quien la use decide qué hacer
     * @param completadaEn el instante en que se cumplió; {@code null} si no se cumplió
     */
    record HoraDeUnaAccion(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, boolean completada,
                           Instant completadaEn) {
    }
}

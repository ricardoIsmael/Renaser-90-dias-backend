package com.renaser.os.mentoring.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * «Los huecos de horario de esta persona en esta semana» (spec 015): lo único que {@code leadership} necesita de
 * {@code mentoring} para su ruta de la ficha. La semana en curso se calcula al pedirla; una semana ya cerrada sale de la
 * foto guardada.
 *
 * <p><b>No autoriza</b>, igual que {@link SemanaDeSeguimientoFinder}: quien llama ya estableció que el actor puede ver
 * a esa persona. Sí rechaza a una persona de cuenta suspendida.
 */
public interface HuecosDeHorarioFinder {

    /**
     * @param lunes el lunes de la semana pedida; {@code null} = la semana en curso de la persona, en SU zona
     * @throws IllegalArgumentException si {@code lunes} no es lunes, es de una semana que todavía no llegó o es
     *         anterior a la primera semana de su programa
     * @throws java.util.NoSuchElementException si la persona no existe
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si su cuenta está suspendida
     */
    HuecosDeLaSemana semanaDe(UserId personaId, LocalDate lunes);
}

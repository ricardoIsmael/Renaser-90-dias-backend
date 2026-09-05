package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cuerpo OPCIONAL de {@code PUT /api/v1/habit-unlocks/{habitId}}. Sin cuerpo — o con
 * {@code unlockDay} nulo — el habito arranca hoy, que es como se comportaba el endpoint antes
 * de que existiera este DTO: los clientes ya publicados no cambian.
 *
 * <p>{@code unlockDay}: para que dia del programa (1-90) quiere el aprendiz que empiece. El
 * rechazo por "ese dia ya paso" no vive aca sino en el caso de uso, porque depende del dia
 * actual del aprendiz y una anotacion no lo conoce.
 */
public record ElegirHabitoRequest(@Min(1) @Max(90) Integer unlockDay) {
}

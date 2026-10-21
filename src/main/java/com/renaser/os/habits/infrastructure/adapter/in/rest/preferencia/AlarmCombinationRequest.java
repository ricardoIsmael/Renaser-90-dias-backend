package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PUT /api/v1/habit-preferences/{habitId}/alarm} y de
 * {@code PUT …/weekdays/{weekday}/alarm}: como suena la alarma (spec 009, D-283).
 *
 * <p>Los cuatro campos obligatorios y {@code Boolean}, no {@code boolean}: omitir uno es un 400 de validacion
 * y no un {@code false} que apagaria el sonido, la vibracion o la repeticion sin que nadie lo pida (el bug del
 * 2026-09-03 con el recordatorio). Que sonido y vibracion no vayan los dos apagados y que la voz necesite el
 * sonido lo valida el dominio ({@link CombinacionDeAlarma}): ambos son un 400.
 */
public record AlarmCombinationRequest(@NotNull Boolean sound, @NotNull Boolean vibration, @NotNull Boolean voice,
                                      @NotNull Boolean repeat) {

    /** @throws IllegalArgumentException si la combinacion no es valida (400) */
    CombinacionDeAlarma toCombination() {
        return CombinacionDeAlarma.de(sound, vibration, voice, repeat);
    }
}

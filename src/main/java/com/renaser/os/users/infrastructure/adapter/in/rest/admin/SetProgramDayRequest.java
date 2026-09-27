package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code programDay} va de 1 a 89 (D-194, decision del dueño 2026-09-26; antes 0..90): el 90
 * gradua en el barrido siguiente y no se deshace, y graduar no se hace por esta via.
 */
public record SetProgramDayRequest(@NotNull @Min(value = 1, message = MENSAJE_RANGO)
                                    @Max(value = 89, message = MENSAJE_RANGO) Integer programDay,
                                    @Size(max = 280) String motivo) {

    static final String MENSAJE_RANGO =
            "El día tiene que estar entre 1 y 89: el 0 y el 90 no se fijan a mano (graduar no se hace desde aquí)";

    /** Compatibilidad con el panel admin actual, que todavia manda solo `programDay`. */
    public SetProgramDayRequest(Integer programDay) {
        this(programDay, null);
    }
}

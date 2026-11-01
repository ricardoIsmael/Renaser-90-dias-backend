package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code programDay} va de 1 a la duracion de su programa menos uno (D-194, decision del dueño 2026-09-26; antes 0..90): el
 * ultimo dia gradua en el barrido siguiente y no se deshace, y graduar no se hace por esta via. Aca solo se acota al mayor
 * programa posible (364); la duracion de la persona (89 en la Escuela) la aplica el dominio (spec 022).
 */
public record SetProgramDayRequest(@NotNull @Min(value = 1, message = MENSAJE_RANGO)
                                    @Max(value = DuracionDelPrograma.MAXIMA - 1, message = MENSAJE_RANGO) Integer programDay,
                                    @Size(max = 280) String motivo) {

    static final String MENSAJE_RANGO =
            "El día tiene que estar entre 1 y " + (DuracionDelPrograma.MAXIMA - 1)
                    + ", y antes del último día de su programa: el 0 y el último día no se fijan a mano (graduar no se hace desde aquí)";

    /** Compatibilidad con el panel admin actual, que todavia manda solo `programDay`. */
    public SetProgramDayRequest(Integer programDay) {
        this(programDay, null);
    }
}

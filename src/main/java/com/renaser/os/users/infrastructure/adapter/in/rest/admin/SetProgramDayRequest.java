package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetProgramDayRequest(@NotNull @Min(0) @Max(90) Integer programDay,
                                    @Size(max = 280) String motivo) {

    /** Compatibilidad con el panel admin actual, que todavia manda solo `programDay`. */
    public SetProgramDayRequest(Integer programDay) {
        this(programDay, null);
    }
}

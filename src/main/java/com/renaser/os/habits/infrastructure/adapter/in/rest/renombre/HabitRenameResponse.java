package com.renaser.os.habits.infrastructure.adapter.in.rest.renombre;

import com.renaser.os.habits.domain.model.renombre.RenombreHabito;

import java.util.UUID;

public record HabitRenameResponse(UUID habitId, String customTitle, String reason) {

    public static HabitRenameResponse from(RenombreHabito r) {
        return new HabitRenameResponse(r.habitoId().value(), r.tituloPersonal(), r.motivo());
    }
}

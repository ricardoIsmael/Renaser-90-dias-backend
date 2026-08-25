package com.renaser.os.habits.domain.model.horario;

import java.util.UUID;

public record HorarioHabitoId(UUID value) {

    public HorarioHabitoId {
        if (value == null) {
            throw new IllegalArgumentException("HorarioHabitoId no puede ser null");
        }
    }

    public static HorarioHabitoId of(UUID value) {
        return new HorarioHabitoId(value);
    }

    public static HorarioHabitoId newId() {
        return new HorarioHabitoId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

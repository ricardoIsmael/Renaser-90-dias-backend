package com.renaser.os.habits.domain.model.radar;

import java.util.UUID;

public record RegistroRadarId(UUID value) {

    public RegistroRadarId {
        if (value == null) {
            throw new IllegalArgumentException("RegistroRadarId no puede ser null");
        }
    }

    public static RegistroRadarId of(UUID value) {
        return new RegistroRadarId(value);
    }

    public static RegistroRadarId newId() {
        return new RegistroRadarId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

package com.renaser.os.shared.domain;

import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId no puede ser null");
        }
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId no puede ser vacio");
        }
        return new UserId(parse(value));
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("UserId no es un UUID valido: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

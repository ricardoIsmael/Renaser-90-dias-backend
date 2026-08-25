package com.renaser.os.academy.domain.model.curso;

/** Identidad de una leccion. Clave natural de Skool, igual que {@link CursoId}. */
public record LeccionId(String value) {

    public LeccionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LeccionId no puede ser vacio");
        }
    }

    public static LeccionId of(String value) {
        return new LeccionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

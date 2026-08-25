package com.renaser.os.academy.domain.model.curso;

/** Identidad de una seccion de curso. Clave natural de Skool, igual que {@link CursoId}. */
public record SeccionCursoId(String value) {

    public SeccionCursoId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SeccionCursoId no puede ser vacio");
        }
    }

    public static SeccionCursoId of(String value) {
        return new SeccionCursoId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

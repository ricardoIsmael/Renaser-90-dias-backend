package com.renaser.os.academy.domain.model.asignacion;

import java.util.Objects;

/** Identidad de una asignacion de curso (`asignaciones_curso.id bigint`). */
public record AsignacionCursoId(long value) {

    public AsignacionCursoId {
        if (value <= 0) {
            throw new IllegalArgumentException("AsignacionCursoId debe ser positivo: " + value);
        }
    }

    public static AsignacionCursoId of(long value) {
        return new AsignacionCursoId(value);
    }

    @Override
    public String toString() {
        return Objects.toString(value);
    }
}

package com.renaser.os.academy.domain.model.asignacion;

import java.util.Objects;

/** Identidad de un grupo (`grupos.id bigint`). */
public record GrupoId(long value) {

    public GrupoId {
        if (value <= 0) {
            throw new IllegalArgumentException("GrupoId debe ser positivo: " + value);
        }
    }

    public static GrupoId of(long value) {
        return new GrupoId(value);
    }

    @Override
    public String toString() {
        return Objects.toString(value);
    }
}

package com.renaser.os.habits.domain.model.radar;

import java.util.UUID;

/**
 * Identidad de un check-in del Codigo Renaser (tabla {@code registros_radar}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code RegistroRadarId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record RegistroRadarId(UUID value) {

    public RegistroRadarId {
        if (value == null) {
            throw new IllegalArgumentException("RegistroRadarId no puede ser null");
        }
    }

    public static RegistroRadarId of(UUID value) {
        return new RegistroRadarId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

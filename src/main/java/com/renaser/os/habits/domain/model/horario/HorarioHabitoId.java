package com.renaser.os.habits.domain.model.horario;

import java.util.UUID;

/**
 * Identidad de un horario del catalogo (tabla {@code horarios_habito}).
 * Valida y envuelve un UUID, pero <b>no lo genera</b>: la generacion
 * vive fuera de {@code domain/}, detras del puerto
 * {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma el id con
 * {@code HorarioHabitoId.of(idGenerator.newId())} antes de invocar la factoria del agregado
 * (CLAUDE.MD 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record HorarioHabitoId(UUID value) {

    public HorarioHabitoId {
        if (value == null) {
            throw new IllegalArgumentException("HorarioHabitoId no puede ser null");
        }
    }

    public static HorarioHabitoId of(UUID value) {
        return new HorarioHabitoId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

package com.renaser.os.rag.domain.model.espejosombra;

import java.util.UUID;

/**
 * Identidad de un Informe Espejo Sombra (tabla {@code informes_espejo_sombra}). Valida y
 * envuelve un UUID, pero <b>no lo genera</b>: la generacion vive fuera de {@code domain/},
 * detras del puerto {@link com.renaser.os.shared.domain.IdGenerator}, y el caso de uso arma
 * el id con {@code InformeEspejoSombraId.of(idGenerator.newId())} antes de invocar la
 * factoria del agregado (CLAUDE.MD sec. 5.4.7: {@code domain/} sin aleatoriedad).
 */
public record InformeEspejoSombraId(UUID value) {

    public InformeEspejoSombraId {
        if (value == null) {
            throw new IllegalArgumentException("InformeEspejoSombraId no puede ser null");
        }
    }

    public static InformeEspejoSombraId of(UUID value) {
        return new InformeEspejoSombraId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

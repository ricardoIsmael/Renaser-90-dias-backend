package com.renaser.os.rag.domain.model.espejosombra;

import java.util.UUID;

/** Identidad de un Informe Espejo Sombra (tabla {@code informes_espejo_sombra}). */
public record InformeEspejoSombraId(UUID value) {

    public InformeEspejoSombraId {
        if (value == null) {
            throw new IllegalArgumentException("InformeEspejoSombraId no puede ser null");
        }
    }

    public static InformeEspejoSombraId of(UUID value) {
        return new InformeEspejoSombraId(value);
    }

    public static InformeEspejoSombraId newId() {
        return new InformeEspejoSombraId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

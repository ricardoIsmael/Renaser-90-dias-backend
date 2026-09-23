package com.renaser.os.rag.domain.model.intencion;

import java.util.List;
import java.util.Objects;

/**
 * Un vector de ejemplo con la etiqueta que representa: una frase tipica de una intencion, o el
 * titulo de un habito de hoy. Una etiqueta puede tener varias referencias (varias formas de pedir
 * lo mismo), y gana la que mas se parezca al mensaje.
 */
public record Referencia(String etiqueta, List<Float> vector) {

    public Referencia {
        Objects.requireNonNull(etiqueta, "etiqueta es obligatoria");
        vector = List.copyOf(Objects.requireNonNull(vector, "vector es obligatorio"));
    }
}

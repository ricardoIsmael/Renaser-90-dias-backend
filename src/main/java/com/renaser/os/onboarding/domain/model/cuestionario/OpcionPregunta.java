package com.renaser.os.onboarding.domain.model.cuestionario;

import java.util.Objects;

/** Una opcion de SELECCION_UNICA/SELECCION_MULTIPLE. Catalogo, solo lectura (ver {@link Seccion}). */
public record OpcionPregunta(int preguntaId, short orden, String valor, String etiqueta) {

    public OpcionPregunta {
        Objects.requireNonNull(valor, "valor es obligatorio");
        Objects.requireNonNull(etiqueta, "etiqueta es obligatoria");
    }
}

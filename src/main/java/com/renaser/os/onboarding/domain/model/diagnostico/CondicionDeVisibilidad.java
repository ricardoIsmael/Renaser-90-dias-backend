package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.Objects;

/** «Se muestra solo si» la respuesta de {@code preguntaId} es {@code valor} (o, en casillas, lo incluye). */
public record CondicionDeVisibilidad(int preguntaId, String valor) {

    public CondicionDeVisibilidad {
        Objects.requireNonNull(valor, "valor es obligatorio");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("La condición necesita la respuesta que activa la pregunta");
        }
    }
}

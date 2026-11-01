package com.renaser.os.onboarding.domain.model.diagnostico;

import java.util.Objects;

/** Una opción (o una fila de la cuadrícula). {@code valor} es lo que se guarda; {@code etiqueta}, lo que se lee. */
public record OpcionDelDiagnostico(String valor, String etiqueta) {

    public OpcionDelDiagnostico {
        Objects.requireNonNull(etiqueta, "etiqueta es obligatoria");
        if (etiqueta.isBlank()) {
            throw new IllegalArgumentException("Una opción no puede estar vacía");
        }
        etiqueta = etiqueta.trim();
        valor = valor == null || valor.isBlank() ? etiqueta : valor.trim();
    }

    public static OpcionDelDiagnostico de(String etiqueta) {
        return new OpcionDelDiagnostico(null, etiqueta);
    }
}

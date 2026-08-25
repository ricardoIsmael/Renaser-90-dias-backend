package com.renaser.os.onboarding.domain.model.cuestionario;

import java.time.Instant;
import java.util.Objects;

/**
 * Catalogo, solo lectura desde este modulo en este alcance (sin seeds ni CRUD admin —
 * decision de alcance, ver docs/MODULO_ONBOARDING.md). Una seccion agrupa preguntas
 * dentro de un {@code flujo} (ej. "onboarding_v90").
 */
public record Seccion(short id, String flujo, String claveSeccion, String titulo, String descripcion, short orden,
                       Instant creadoEn) {

    public Seccion {
        Objects.requireNonNull(flujo, "flujo es obligatorio");
        Objects.requireNonNull(claveSeccion, "claveSeccion es obligatoria");
        Objects.requireNonNull(titulo, "titulo es obligatorio");
    }
}

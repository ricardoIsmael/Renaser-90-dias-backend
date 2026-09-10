package com.renaser.os.points.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Tramo atribuible a un mentor sobre un alumno, ya intersectado con el mes local y con la
 * pertenencia real del alumno. Semiabierto {@code [desde, hasta)}, igual que los intervalos
 * de `community`.
 *
 * <p>Es un tipo propio de `points` a propósito: el intervalo de asignación vive en el dominio
 * de `community` y un módulo no importa el dominio de otro (CLAUDE.MD §5.1). Quien llama
 * traduce en el borde.
 */
public record VentanaEvaluacion(Instant desde, Instant hasta) {

    public VentanaEvaluacion {
        Objects.requireNonNull(desde, "desde es obligatorio");
        if (hasta != null && !hasta.isAfter(desde)) {
            throw new IllegalArgumentException("Una ventana de evaluacion no puede durar cero ni ir hacia atras");
        }
    }

    /** {@code desde <= instante < hasta}. */
    public boolean contiene(Instant instante) {
        return !instante.isBefore(desde) && (hasta == null || instante.isBefore(hasta));
    }
}

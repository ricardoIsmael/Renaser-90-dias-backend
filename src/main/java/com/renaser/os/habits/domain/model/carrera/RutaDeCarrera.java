package com.renaser.os.habits.domain.model.carrera;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * La ruta de una carrera (spec 042, FR-040 y FR-043): la polilínea codificada que mandó el teléfono, ya simplificada y sin los
 * primeros ni los últimos 200 m, y cuántos puntos tiene. Opcional: la persona puede no guardarla, y el servidor la borra a los
 * 180 días (FR-042).
 */
public record RutaDeCarrera(String polyline, int puntos) {

    /** Lo que admite la columna {@code carreras.ruta_polyline} (V170): 100 KB. */
    public static final int BYTES_MAXIMOS = 100 * 1024;
    public static final int PUNTOS_MAXIMOS = 3000;

    public RutaDeCarrera {
        Objects.requireNonNull(polyline, "La ruta es obligatoria");
        if (polyline.isBlank()) {
            throw new IllegalArgumentException("La ruta viene vacía. Si no quieres guardarla, envía la carrera sin ruta.");
        }
        if (polyline.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
            throw new IllegalArgumentException("La ruta es demasiado larga para guardarla.");
        }
        if (puntos < 2 || puntos > PUNTOS_MAXIMOS) {
            throw new IllegalArgumentException("La ruta tiene que tener entre 2 y " + PUNTOS_MAXIMOS + " puntos.");
        }
        PolylineDeGoogle.decodificar(polyline);
    }

    /** Su largo en metros (haversine entre los puntos). */
    public double largoEnMetros() {
        return PolylineDeGoogle.largoEnMetros(polyline);
    }
}

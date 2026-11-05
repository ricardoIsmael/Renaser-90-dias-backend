package com.renaser.os.habits.domain.model.carrera;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * La ruta tal como la mandó el teléfono (spec 042, FR-040 y FR-043): completa, simplificada y codificada, CON la zona de
 * privacidad. Nunca se guarda así: {@link #sinLaZonaDePrivacidad()} da la {@link RutaDeCarrera} que sí se guarda. Los topes de
 * acá solo acotan el trabajo de leerla (el doble de los que se guardan); los de verdad (3000 puntos y 100 KB) se aplican después
 * de recortar, en {@link RutaDeCarrera}.
 *
 * <p>{@code toString} no muestra la polilínea: son coordenadas de una persona y no van a ningún log.
 */
public record RutaEnviada(String polyline, int puntos) {

    public static final int BYTES_MAXIMOS = 2 * RutaDeCarrera.BYTES_MAXIMOS;
    public static final int PUNTOS_MAXIMOS = 2 * RutaDeCarrera.PUNTOS_MAXIMOS;

    public RutaEnviada {
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
    }

    /**
     * La ruta que se guarda: sin los primeros ni los últimos 200 m ({@link ZonaDePrivacidad}), vuelta a codificar. Vacío si la
     * carrera no llega a dejar ruta después del recorte (la carrera se guarda igual, sin ruta).
     *
     * @throws IllegalArgumentException si no se puede leer o si, ya recortada, pasa los topes de lo que se guarda
     */
    public Optional<RutaDeCarrera> sinLaZonaDePrivacidad() {
        List<double[]> recortada = ZonaDePrivacidad.recortar(PolylineDeGoogle.decodificar(polyline));
        if (recortada.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new RutaDeCarrera(PolylineDeGoogle.codificar(recortada), recortada.size()));
    }

    @Override
    public String toString() {
        return "RutaEnviada[puntos=" + puntos + "]";
    }
}

package com.renaser.os.shared.domain;

/**
 * Cuánto dura el programa de una persona, en días (spec 022, D-295): 90 en la Escuela y la Certificación; 90 o 365 en el Diplomado
 * (3 meses = 90 días y 1 año = 365, decisión Q4-A). Es el tope del día y el día de la graduación de ESA persona: ya no hay un 90 escrito de
 * antemano. La duración de cada persona se guarda en {@code participantes_programa.duracion_dias} al aceptarla, así que cambiar las
 * opciones del catálogo no mueve a quien ya empezó (FR-004).
 */
public final class DuracionDelPrograma {

    /** La duración de siempre (Escuela y Certificación; 3 meses del Diplomado). */
    public static final int POR_DEFECTO = CortesDeFase.DURACION_BASE;
    public static final int MINIMA = 1;
    /** El máximo que admite la base ({@code CHECK duracion_dias BETWEEN 1 AND 365}): 1 año. */
    public static final int MAXIMA = 365;

    private DuracionDelPrograma() {
    }

    /**
     * @throws IllegalArgumentException si no está entre {@link #MINIMA} y {@link #MAXIMA} (400)
     */
    public static int validar(int dias) {
        if (dias < MINIMA || dias > MAXIMA) {
            throw new IllegalArgumentException("La duración del programa va de " + MINIMA + " a " + MAXIMA + " días, llegó " + dias + ".");
        }
        return dias;
    }

    /**
     * Cómo se dice la duración: «1 año», «2 años», «3 meses» o «90 días». Los meses y los años son los del dueño (3 meses = 90 días,
     * 1 año = 365); otro valor se dice en días.
     */
    public static String etiqueta(int dias) {
        if (dias % MAXIMA == 0) {
            int anios = dias / MAXIMA;
            return anios == 1 ? "1 año" : anios + " años";
        }
        if (dias % POR_DEFECTO == 0) {
            return dias / 30 + " meses";
        }
        return dias + " días";
    }
}

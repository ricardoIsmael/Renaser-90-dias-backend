package com.renaser.os.rag.domain.model.analisis;

import java.util.List;
import java.util.Objects;

/**
 * Las cuatro partes del análisis, siempre en este orden (spec 031, FR-007): lo que más movió, lo que se te fue, cómo estuviste y los pasos
 * para el próximo periodo (de uno a tres). Se lee en menos de un minuto, así que cada parte es corta. Es texto de la IA sobre datos
 * personales: no se loguea.
 *
 * <p>Esta clase solo exige la forma (nada vacío, nada largo). Que las cifras coincidan con las calculadas y que no haya diagnóstico lo
 * revisa {@link ValidadorDelTexto}: son dos preguntas distintas (¿tiene forma de análisis? / ¿se puede mostrar?).
 */
public record TextoDelAnalisis(String loQueMasMovio, String loQueSeTeFue, String comoEstuviste, List<String> proximosPasos) {

    public static final int MAXIMO_POR_PARTE = 700;
    public static final int MAXIMO_POR_PASO = 280;
    public static final int MINIMO_DE_PASOS = 1;
    public static final int MAXIMO_DE_PASOS = 3;

    public TextoDelAnalisis {
        loQueMasMovio = parte(loQueMasMovio, "loQueMasMovio", MAXIMO_POR_PARTE);
        loQueSeTeFue = parte(loQueSeTeFue, "loQueSeTeFue", MAXIMO_POR_PARTE);
        comoEstuviste = parte(comoEstuviste, "comoEstuviste", MAXIMO_POR_PARTE);
        Objects.requireNonNull(proximosPasos, "proximosPasos es obligatorio");
        proximosPasos = proximosPasos.stream().map(paso -> parte(paso, "proximosPasos", MAXIMO_POR_PASO)).toList();
        if (proximosPasos.size() < MINIMO_DE_PASOS || proximosPasos.size() > MAXIMO_DE_PASOS) {
            throw new IllegalArgumentException("Los pasos son de " + MINIMO_DE_PASOS + " a " + MAXIMO_DE_PASOS + ": " + proximosPasos.size());
        }
    }

    /** Todas las partes juntas, para revisarlas con una sola pasada. */
    public List<String> partes() {
        List<String> partes = new java.util.ArrayList<>(List.of(loQueMasMovio, loQueSeTeFue, comoEstuviste));
        partes.addAll(proximosPasos);
        return List.copyOf(partes);
    }

    private static String parte(String texto, String nombre, int maximo) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(nombre + " es obligatorio");
        }
        String limpio = texto.strip();
        if (limpio.codePointCount(0, limpio.length()) > maximo) {
            throw new IllegalArgumentException(nombre + " supera " + maximo + " caracteres");
        }
        return limpio;
    }
}

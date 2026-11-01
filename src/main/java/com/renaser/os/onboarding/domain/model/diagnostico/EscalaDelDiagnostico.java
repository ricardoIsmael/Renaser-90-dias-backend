package com.renaser.os.onboarding.domain.model.diagnostico;

/** El rango de una escala o de las filas de una cuadrícula. La base guarda la respuesta de una escala entre 1 y 10 ({@code valor_escala}). */
public record EscalaDelDiagnostico(int min, int max, String etiquetaMin, String etiquetaMax) {

    public static final EscalaDelDiagnostico DEL_1_AL_5 = new EscalaDelDiagnostico(1, 5, null, null);

    public EscalaDelDiagnostico {
        if (min < 1 || max > 10 || min >= max) {
            throw new IllegalArgumentException("La escala va de " + 1 + " a " + 10 + " y su mínimo es menor que su máximo");
        }
        etiquetaMin = vacioANulo(etiquetaMin);
        etiquetaMax = vacioANulo(etiquetaMax);
    }

    public boolean contiene(int puntaje) {
        return puntaje >= min && puntaje <= max;
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

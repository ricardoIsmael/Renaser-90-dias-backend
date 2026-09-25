package com.renaser.os.points.api;

/**
 * Color del semáforo de cumplimiento del APRENDIZ (D-168). No confundir con el semáforo
 * operativo del mentor ({@code users.MentorOperationalStatus}): no comparten umbrales ni se
 * derivan uno del otro.
 *
 * <p>Los umbrales (verde ≥ 80, amarillo 60–79,9, rojo &lt; 60) viven en un solo lugar, el
 * dominio de {@code points} ({@code ReglaDelSemaforo}). Acá solo está el vocabulario y la
 * palabra que acompaña siempre al color (RL-30: nunca se comunica un estado solo con color).
 */
public enum ColorSemaforo {

    VERDE("Al día"),
    AMARILLO("Requiere atención"),
    ROJO("Con problemas"),
    /** No hubo nada programado en la ventana. Nunca se pinta verde por falta de datos (D-128). */
    SIN_DATOS("Sin datos");

    private final String etiqueta;

    ColorSemaforo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** La palabra que se muestra junto al color. */
    public String etiqueta() {
        return etiqueta;
    }
}

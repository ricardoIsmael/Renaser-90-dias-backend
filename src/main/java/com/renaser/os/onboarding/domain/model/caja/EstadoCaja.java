package com.renaser.os.onboarding.domain.model.caja;

/**
 * En qué va la Caja Renaser de un aprendiz (D-219, docs/specs/CAJA_RENASER.md §2).
 *
 * <p><b>Derivar, no incrementar</b> (regla 02): {@link #NO_APLICA}, {@link #EN_EVALUACION},
 * {@link #POR_REVISAR} (el automático), {@link #EN_PAUSA} y {@link #FUERA_DE_LA_APP} se calculan al leer;
 * solo los pasos que hace una persona se guardan ({@link TipoPasoCaja}). El nombre de cada valor es el que
 * viaja por la API; {@link #enPalabras()} es como se dice en un mensaje que lee una persona.
 */
public enum EstadoCaja {

    /** Día del programa menor que 8 (o no es un aprendiz con el programa activado). */
    NO_APLICA("todavía no aplica"),
    /** Día ≥ 8 y no cumplió el requisito de la Fase 1: el Admin la aprueba caso por caso. */
    EN_EVALUACION("está en evaluación"),
    /** Día ≥ 8 y cumplió el requisito, o el Admin la aprobó. */
    POR_REVISAR("está en revisión"),
    ARMANDO("se está armando"),
    ENVIADA("ya va en camino"),
    ENTREGADA("ya fue entregada"),
    CON_PROBLEMA("tiene un problema reportado"),
    /** La cuenta está suspendida y la caja no se entregó. */
    EN_PAUSA("está en pausa"),
    /** País distinto de Perú: el dueño, «extranjero, fuera de la app». */
    FUERA_DE_LA_APP("se envía fuera de la app");

    private final String enPalabras;

    EstadoCaja(String enPalabras) {
        this.enPalabras = enPalabras;
    }

    /**
     * Cómo se dice en un mensaje: «La caja ya fue entregada». El nombre del valor (`ENTREGADA`) es el de la
     * API y no se le muestra a nadie (E-416: el 409 lo mostraba tal cual en la app).
     */
    public String enPalabras() {
        return enPalabras;
    }
}

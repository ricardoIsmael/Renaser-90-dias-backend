package com.renaser.os.community.domain.model.acompanamiento;

/**
 * Recepción y grupo estable son la misma tabla `celulas` con distinta política, no dos
 * entidades. Así la recepción reutiliza su conversación CELULA y el traslado no obliga a
 * migrar de tabla (plan.md §3).
 */
public enum TipoCelula {

    /** Primeros días del programa. Sin tope comercial (D-05) y puede no tener mentor. */
    RECEPCION,

    /** Grupo estable de 10 configurable hasta 15 (D-01). */
    REGULAR
}

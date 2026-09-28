package com.renaser.os.onboarding.domain.model.caja;

/**
 * En qué va la Caja Renaser de un aprendiz (D-219, docs/specs/CAJA_RENASER.md §2).
 *
 * <p><b>Derivar, no incrementar</b> (regla 02): {@link #NO_APLICA}, {@link #EN_EVALUACION},
 * {@link #POR_REVISAR} (el automático), {@link #EN_PAUSA} y {@link #FUERA_DE_LA_APP} se calculan al leer;
 * solo los pasos que hace una persona se guardan ({@link TipoPasoCaja}). El nombre de cada valor es el que
 * viaja por la API.
 */
public enum EstadoCaja {

    /** Día del programa menor que 8 (o no es un aprendiz con el programa activado). */
    NO_APLICA,
    /** Día ≥ 8 y no cumplió el requisito de la Fase 1: el Admin la aprueba caso por caso. */
    EN_EVALUACION,
    /** Día ≥ 8 y cumplió el requisito, o el Admin la aprobó. */
    POR_REVISAR,
    ARMANDO,
    ENVIADA,
    ENTREGADA,
    CON_PROBLEMA,
    /** La cuenta está suspendida y la caja no se entregó. */
    EN_PAUSA,
    /** País distinto de Perú: el dueño, «extranjero, fuera de la app». */
    FUERA_DE_LA_APP
}

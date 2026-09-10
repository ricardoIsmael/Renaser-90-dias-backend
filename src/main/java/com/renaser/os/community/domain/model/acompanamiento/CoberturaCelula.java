package com.renaser.os.community.domain.model.acompanamiento;

/**
 * Quién responde por el grupo en un instante. Existe para que "sin mentor" no se muestre
 * como "no tienes grupo": son cosas distintas y confundirlas hace desaparecer el chat de un
 * aprendiz que sí tiene grupo (plan.md §10).
 */
public enum CoberturaCelula {

    /** Hay un mentor vigente. */
    CON_MENTOR,

    /** No hay mentor, pero soporte cubre. El grupo funciona (P-03). */
    SOPORTE,

    /** Ni mentor ni soporte: hueco real, visible para el administrador. Nunca se disimula. */
    SIN_COBERTURA
}

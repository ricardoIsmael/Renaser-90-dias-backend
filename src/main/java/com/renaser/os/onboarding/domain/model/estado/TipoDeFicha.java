package com.renaser.os.onboarding.domain.model.estado;

/**
 * Qué ficha completa una persona en su ingreso (spec 026): la estándar de siempre o el diagnóstico. Es el valor de
 * {@code estado_onboarding.ficha}; las personas que existen antes de la spec 026 son {@link #ESTANDAR}.
 */
public enum TipoDeFicha {
    ESTANDAR,
    CON_DIAGNOSTICO
}

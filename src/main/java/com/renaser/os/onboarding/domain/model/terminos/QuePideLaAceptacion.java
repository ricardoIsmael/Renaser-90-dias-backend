package com.renaser.os.onboarding.domain.model.terminos;

/**
 * Qué le toca hacer a una persona con los términos de su formación (spec 027):
 * <ul>
 *   <li>{@link #NADA}: aceptó la vigente, o ninguna versión posterior pidió aceptar de nuevo, o su formación todavía no publicó términos.</li>
 *   <li>{@link #PRIMERA_VEZ}: nunca aceptó ningunos. Es el paso de términos del primer ingreso; el onboarding la lleva, no hay pantalla que la bloquee.</li>
 *   <li>{@link #NUEVA_VERSION}: aceptó una versión anterior y una posterior pidió aceptar de nuevo (FR-016): la app lo bloquea hasta aceptar.</li>
 *   <li>{@link #PRIMERA_EN_LA_FORMACION}: ya aceptó los términos de otra formación y entra a esta (personal asignado, aprendiz trasladado): debe aceptar los de
 *       la nueva (Q4-A, Q11-A). También bloquea.</li>
 * </ul>
 */
public enum QuePideLaAceptacion {
    NADA,
    PRIMERA_VEZ,
    NUEVA_VERSION,
    PRIMERA_EN_LA_FORMACION;

    /** Si la app debe mostrar la pantalla de aceptación antes de dejar usar el resto (FR-016). */
    public boolean bloquea() {
        return this == NUEVA_VERSION || this == PRIMERA_EN_LA_FORMACION;
    }
}

package com.renaser.os.community.domain.model.acompanamiento;

/**
 * Qué hace una persona dentro de un grupo. No es un {@code UserRole}: el rol es global y lo
 * gobierna `users`; esto es una función asignada dentro de una célula concreta y con fechas
 * (clarifications.md: "GUIA no es nuevo UserRole"). La misma persona puede ser APRENDIZ en
 * su grupo y MENTOR en otro sin que ninguno de los dos hechos cambie su rol.
 */
public enum FuncionAcompanamiento {

    /** Cursa el programa dentro del grupo. Es la única función que consume un lugar. */
    APRENDIZ,

    /** Acompaña al grupo y es evaluado por el cumplimiento de sus aprendices. */
    MENTOR,

    /** Atiende la recepción de una cohorte. Puede cubrir varias a la vez. */
    GUIA,

    /** ADMIN/ALCHEMIST cubriendo un grupo. Permanece aunque el mentor rote (D-06). */
    SOPORTE;

    /**
     * El cupo comercial cuenta aprendices, no cabezas. Si mentor y soporte contaran, un
     * grupo "de 10" aceptaría 8 alumnos y el número dejaría de significar lo que dice
     * (plan.md §3).
     */
    public boolean consumeCupo() {
        return this == APRENDIZ;
    }

    /** Funciones de las que solo puede haber una vigente por grupo a la vez. */
    public boolean esExclusivaPorCelula() {
        return this == MENTOR;
    }
}

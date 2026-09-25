package com.renaser.os.points.api;

/**
 * Por qué un día del semáforo tiene o no tiene porcentaje. Cuatro de los cinco estados existen
 * para no confundir "no había nada" con "no cumplió" (plan.md §7 del SDD 001: que falte
 * información no es lo mismo que incumplir).
 */
public enum EstadoDiaSemaforo {

    /** Tuvo algo programado: tiene porcentaje y color. */
    MEDIDO,
    /** No tuvo nada programado (ni hábitos ni objetivos). No entra al promedio. */
    SIN_DATOS,
    /** Staff con programa propio que pausó su semáforo ese día. No entra al promedio. */
    PAUSADO,
    /** Día cerrado que el barrido horario todavía no calculó. No entra al promedio. */
    PENDIENTE,
    /** Antes del día 1 o después del día 90 del programa. No entra al promedio. */
    FUERA_DEL_PROGRAMA
}

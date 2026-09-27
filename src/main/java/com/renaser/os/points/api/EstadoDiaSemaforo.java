package com.renaser.os.points.api;

/**
 * Por qué un día del semáforo tiene o no tiene porcentaje. Cinco de los seis estados existen
 * para no confundir "no había nada" con "no cumplió" (plan.md §7 del SDD 001: que falte
 * información no es lo mismo que incumplir).
 *
 * <p>Viaja como texto en el JSON ({@code name()}). Un estado que la app instalada no conoce lo
 * lee como «desconocido» y lo pinta neutro, sin romper la pantalla (verificado en el
 * {@code semaforoSchemas.ts} del frontend publicado, 2026-09-27).
 */
public enum EstadoDiaSemaforo {

    /** Tuvo algo programado: tiene porcentaje y color. */
    MEDIDO,
    /** No tuvo nada programado (ni hábitos ni objetivos). No entra al promedio. */
    SIN_DATOS,
    /** Staff con programa propio que pausó su semáforo ese día. No entra al promedio. */
    PAUSADO,
    /**
     * La cuenta estuvo suspendida ese día, aunque sea un rato: el de la suspensión, el de la
     * reactivación y los del medio (D-209). No entra al promedio.
     */
    CUENTA_SUSPENDIDA,
    /** Día cerrado que el barrido horario todavía no calculó. No entra al promedio. */
    PENDIENTE,
    /** Antes del día 1 o después del día 90 del programa. No entra al promedio. */
    FUERA_DEL_PROGRAMA
}

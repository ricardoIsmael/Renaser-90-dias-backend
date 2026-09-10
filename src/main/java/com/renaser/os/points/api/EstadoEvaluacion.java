package com.renaser.os.points.api;

/**
 * Por qué un porcentaje puede venir en {@code null}. Existe para que la ausencia de datos no
 * se muestre como 0 % ni como "al día": son tres cosas distintas y confundirlas califica mal
 * a una persona (plan.md §8).
 */
public enum EstadoEvaluacion {

    /** Hubo al menos un alumno con obligaciones vencidas en la ventana. */
    CALCULADA,

    /** Hubo tramos asignados, pero ninguna obligación vencida que medir. */
    SIN_MUESTRA,

    /** No hay tramos verificables: antes de la migración el historial no existe. */
    SIN_HISTORIAL
}

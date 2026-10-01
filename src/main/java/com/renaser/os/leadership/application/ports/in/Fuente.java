package com.renaser.os.leadership.application.ports.in;

/**
 * Un dato que sale de otro módulo, con la marca de si esa fuente respondió (SDD 002, RL-21): una
 * fuente caída no vacía la ficha ni el reporte; se marca como no disponible y el resto se entrega.
 *
 * <p>{@code disponible = false} nunca se muestra como cero ni como «al día» (RL-05).
 */
public record Fuente<T>(T valor, boolean disponible) {

    public static <T> Fuente<T> de(T valor) {
        return new Fuente<>(valor, true);
    }

    public static <T> Fuente<T> noDisponible() {
        return new Fuente<>(null, false);
    }
}

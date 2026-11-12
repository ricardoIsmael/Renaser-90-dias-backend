package com.renaser.os.shared.application;

/**
 * Lo que la política del límite por cuenta decide para un pedido (spec 050, FR-016).
 *
 * @param accion           qué hacer con el pedido
 * @param ventanaPasada    la ventana que ya estaba en su tope ({@code 1m}, {@code 1h}, {@code 1d}); vacía si pasó
 * @param reintentarEnSegundos lo que va en {@code Retry-After}; 0 si no se rechaza
 */
public record DecisionDeLimite(Accion accion, String ventanaPasada, int reintentarEnSegundos) {

    public enum Accion {
        /** Pasa, y no hay nada que anotar. */
        PASAR,
        /** Pasa, pero el grupo estaba en {@code REGISTRAR} y habría sido bloqueado: el filtro deja la línea del registro. */
        PASAR_Y_ANOTAR,
        /** El grupo está en {@code APLICAR} y se pasó: 429. */
        RECHAZAR
    }

    static final DecisionDeLimite PASAR = new DecisionDeLimite(Accion.PASAR, "", 0);

    public static DecisionDeLimite pasar() {
        return PASAR;
    }

    public boolean rechaza() {
        return accion == Accion.RECHAZAR;
    }

    public boolean habriaBloqueado() {
        return accion == Accion.PASAR_Y_ANOTAR;
    }
}

package com.renaser.os.shared.domain;

import java.util.Locale;

/**
 * Lo que pasó con un pedido contado por el filtro de límite por cuenta: el valor de la etiqueta {@code resultado} de
 * {@code renaser_limite_pedidos_total} (spec 050, FR-019).
 */
public enum ResultadoDelLimite {

    PERMITIDO,
    HABRIA_BLOQUEADO,
    BLOQUEADO,
    /** Redis falló o tardó: el pedido pasó sin contarse (FR-017). */
    ERROR_DE_REDIS;

    /** {@code habria_bloqueado}: el texto de la etiqueta de la métrica. */
    public String etiqueta() {
        return name().toLowerCase(Locale.ROOT);
    }
}

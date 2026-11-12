package com.renaser.os.shared.domain;

/**
 * Qué hace el filtro de límite por cuenta con un grupo (spec 050, FR-016). Todos los grupos nacen en {@link #REGISTRAR}.
 */
public enum ModoDeLimite {

    /** No cuenta ni anota nada: el grupo no existe para el filtro. */
    DESACTIVADO,

    /** Cuenta igual y anota «habría bloqueado», pero el pedido pasa: nunca responde 429. Es lo que se mide durante dos semanas. */
    REGISTRAR,

    /** Cuenta y, pasado el tope, responde 429 con {@code Retry-After}. */
    APLICAR
}

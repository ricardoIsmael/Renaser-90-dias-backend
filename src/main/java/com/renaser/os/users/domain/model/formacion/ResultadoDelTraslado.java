package com.renaser.os.users.domain.model.formacion;

/**
 * Cómo terminó la participación que se deja al pasar de formación. Los valores son los de la columna {@code participaciones_anteriores.resultado}.
 */
public enum ResultadoDelTraslado {

    /** El programa llegó a su último día (graduación). */
    TERMINADA,
    /** Administración la dio por terminada antes, con un motivo escrito (Q1-A). */
    DADA_POR_TERMINADA
}

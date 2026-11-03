package com.renaser.os.onboarding.domain.model.pagos;

/** Dónde está la confirmación del cronograma de una persona (spec 032). Se deriva al leer, comparando huellas: no se guarda. */
public enum EstadoDeConfirmacion {

    /** Todavía no confirmó. */
    SIN_CONFIRMAR,

    /** Confirmó este mismo cronograma. */
    CONFIRMADA,

    /** Confirmó uno anterior: Administración cambió fechas o montos desde entonces y la confirmación se vuelve a pedir. */
    DESACTUALIZADA
}

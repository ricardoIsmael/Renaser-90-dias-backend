package com.renaser.os.onboarding.domain.model.pagos;

/**
 * Una cuota solo tiene dos estados (spec 032): pendiente o pagada. No hay «vencida» ni «atrasada»: la maqueta del dueño no los tiene y su decisión es que nada reprocha ni
 * bloquea. Administración la marca a mano; nada se deriva de la fecha.
 */
public enum EstadoDeCuota {
    PENDIENTE,
    PAGADA
}

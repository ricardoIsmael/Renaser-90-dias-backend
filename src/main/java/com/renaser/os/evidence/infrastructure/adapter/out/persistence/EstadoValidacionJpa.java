package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

/** Espejo del tipo Postgres {@code estado_validacion}. */
public enum EstadoValidacionJpa {
    PENDIENTE,
    VALIDA,
    RECHAZADA,
    REVISION_MANUAL,
    ANULADA_ADMIN
}

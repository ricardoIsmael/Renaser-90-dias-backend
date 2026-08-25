package com.renaser.os.users.domain.model.accountrequest;

/**
 * Estado de una solicitud de alta (tabla `solicitudes_cuenta` del SQL:
 * docs/db/sql/BD_NUEVA_V1.sql, tipo `estado_solicitud`).
 */
public enum AccountRequestStatus {

    PENDING,
    APPROVED,
    REJECTED;

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isDecided() {
        return this != PENDING;
    }
}

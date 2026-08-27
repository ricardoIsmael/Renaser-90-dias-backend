package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;

/** PUBLIC_ENDPOINT (gap #9): "mi solicitud". Proyeccion minima, sin datos personales. */
public record AccountRequestStatusResponse(AccountRequestStatus status, String rejectionReason) {
}

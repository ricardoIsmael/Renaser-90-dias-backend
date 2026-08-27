package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;

import java.time.Instant;
import java.util.UUID;

/** Proyeccion a mano del panel admin (gap #9) — nunca la entidad JPA serializada. */
public record AccountRequestResponse(UUID id, String email, String fullName, String phone, String city,
                                      AccountRequestStatus status, String rejectionReason, Instant createdAt,
                                      Instant reviewedAt) {

    public static AccountRequestResponse from(AccountRequest request) {
        return new AccountRequestResponse(request.id().value(), request.email().value(), request.fullName(),
                request.phone(), request.city(), request.status(), request.rejectionReason(), request.createdAt(),
                request.reviewedAt());
    }
}

package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;

import java.util.Objects;

/**
 * PUBLIC_ENDPOINT (gap #9): "mi solicitud", sin autenticar — el solicitante todavia NO
 * tiene un {@code User} (esta pendiente de aprobacion), asi que no hay `X-Actor-Id`
 * posible. Se resuelve por el {@link AccountRequestId} que el cliente ya guardo del
 * 202 de {@code POST /account-requests} (decision de diseño, ver
 * docs/MODULO_USERS.md/PLAN_INTEGRACION_FRONTEND.md #9): un UUID v4 no adivinable es
 * mas seguro que resolver por email, que abriria una enumeracion de que emails tienen
 * solicitud (probar "¿existe una solicitud para X@Y.com?" a fuerza bruta).
 */
public interface CheckAccountRequestStatusUseCase {

    AccountRequestStatusView consultar(AccountRequestId requestId);

    record AccountRequestStatusView(AccountRequestStatus status, String rejectionReason) {

        public AccountRequestStatusView {
            Objects.requireNonNull(status, "status es obligatorio");
        }
    }
}

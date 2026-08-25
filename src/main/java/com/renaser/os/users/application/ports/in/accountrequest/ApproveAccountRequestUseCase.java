package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.NotNull;

/**
 * Aprobar en panel admin. Transaccion unica: crea User + perfil correspondiente +
 * marca AccountRequest.APPROVED (CLAUDE.MD §4.3).
 */
public interface ApproveAccountRequestUseCase {

    void approve(ApproveAccountRequestCommand command);

    record ApproveAccountRequestCommand(
            @NotNull AccountRequestId accountRequestId,
            @NotNull UserId actorId) {

        public ApproveAccountRequestCommand {
            SelfValidating.validateConstructorArgs(ApproveAccountRequestCommand.class, accountRequestId, actorId);
        }
    }
}

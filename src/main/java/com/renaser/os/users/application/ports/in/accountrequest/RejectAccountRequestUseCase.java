package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Rechazar en panel. Debe liberar el email via SupabaseAdminAuthPort (anti-squatting, §5.3.6). */
public interface RejectAccountRequestUseCase {

    void reject(RejectAccountRequestCommand command);

    record RejectAccountRequestCommand(
            @NotNull AccountRequestId accountRequestId,
            @NotNull UserId actorId,
            @NotBlank String reason) {

        public RejectAccountRequestCommand {
            SelfValidating.validateConstructorArgs(RejectAccountRequestCommand.class,
                    accountRequestId, actorId, reason);
        }
    }
}

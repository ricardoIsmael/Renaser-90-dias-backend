package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Rechazar en panel. Debe liberar el email borrando al usuario que el alta ya creo
 * (anti-squatting, CLAUDE.MD §5.3.6): el borrado es local, contra nuestro Postgres y dentro
 * de la misma transaccion. */
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

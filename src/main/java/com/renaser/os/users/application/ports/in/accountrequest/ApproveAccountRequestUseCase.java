package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.NotNull;

/**
 * Aprobar en panel admin. Transaccion unica: crea User + perfil correspondiente +
 * marca AccountRequest.APPROVED (CLAUDE.MD §4.3).
 *
 * <p><b>{@code rol}</b> (D-265): con qué rol entra. Aprobar con un rol que no es TRAINEE es exactamente aprobar como
 * aprendiz y cambiarle el rol en la misma transacción —mismo guard, mismo perfil de mentor, mismo
 * {@code RolDeUsuarioCambiadoEvent}—, así que no hay un segundo camino de alta que pueda quedar distinto del de
 * «Equipo y roles».
 */
public interface ApproveAccountRequestUseCase {

    void approve(ApproveAccountRequestCommand command);

    record ApproveAccountRequestCommand(
            @NotNull AccountRequestId accountRequestId,
            @NotNull UserId actorId,
            @NotNull UserRole rol) {

        public ApproveAccountRequestCommand {
            SelfValidating.validateConstructorArgs(ApproveAccountRequestCommand.class, accountRequestId, actorId, rol);
        }

        /** Sin rol elegido entra como aprendiz: es lo que mandan la app 1.5.0 y el panel de solicitudes. */
        public ApproveAccountRequestCommand(AccountRequestId accountRequestId, UserId actorId) {
            this(accountRequestId, actorId, UserRole.defaultForSelfRegistration());
        }
    }
}

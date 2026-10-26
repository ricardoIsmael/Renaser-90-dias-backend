package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;

import java.util.UUID;

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

    /**
     * @param rolPersonalizadoId opcional (spec 002, fase 2; D-272): con él entra con ese rol personalizado —el rol base
     *                           sale de él y {@code rol} se ignora—, por el mismo caso de uso que «Equipo y roles»
     */
    record ApproveAccountRequestCommand(AccountRequestId accountRequestId, UserId actorId, UserRole rol,
                                        UUID rolPersonalizadoId) {

        /**
         * Validación a mano y no con {@code SelfValidating}: con varios constructores, ese ayudante valida el primero
         * que devuelve la reflexión, que no tiene por qué ser el canónico.
         */
        public ApproveAccountRequestCommand {
            if (accountRequestId == null || actorId == null || rol == null) {
                throw new IllegalArgumentException("Faltan la solicitud, quien aprueba o el rol");
            }
        }

        /** Sin rol personalizado (D-265). */
        public ApproveAccountRequestCommand(AccountRequestId accountRequestId, UserId actorId, UserRole rol) {
            this(accountRequestId, actorId, rol, null);
        }

        /** Sin rol elegido entra como aprendiz: es lo que mandan la app 1.5.0 y el panel de solicitudes. */
        public ApproveAccountRequestCommand(AccountRequestId accountRequestId, UserId actorId) {
            this(accountRequestId, actorId, UserRole.defaultForSelfRegistration(), null);
        }
    }
}

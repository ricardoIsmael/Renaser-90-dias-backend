package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;

import java.util.Objects;

/**
 * Panel admin de solicitudes de cuenta (gap #9): borrar una fila de `solicitudes_cuenta`.
 * Se permite en CUALQUIER estado (PENDING/APPROVED/REJECTED). Si la solicitud estaba PENDIENTE,
 * borra ademas la cuenta que el alta ya habia creado y que nunca se aprobo (INACTIVE): sin eso
 * quedaba huerfana, con el correo tomado para siempre. La cuenta de una solicitud aprobada es
 * de verdad y no se toca. No confirmado con producto si conviene restringir el borrado a estados
 * ya decididos — se documenta como supuesto, no como regla inventada (CLAUDE.MD §0.6).
 *
 * <p><b>Corregido 2026-09-27 (E-368).</b> Decia que borrar la solicitud «no afecta al `User`
 * que ya se haya creado … asi que borrar la solicitud no deja huerfano a nadie». Se escribio
 * antes de que el alta creara al usuario (2026-08-27); desde entonces si lo dejaba huerfano
 * (HALLAZGO-A3 del e2e: volver a pedir el alta daba 409).
 */
public interface DeleteAccountRequestUseCase {

    void eliminar(DeleteAccountRequestCommand command);

    record DeleteAccountRequestCommand(UserId actorId, AccountRequestId requestId) {

        public DeleteAccountRequestCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(requestId, "requestId es obligatorio");
        }
    }
}

package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;

import java.util.Objects;

/**
 * Panel admin de solicitudes de cuenta (gap #9): borrar una fila de `solicitudes_cuenta`.
 * Se permite en CUALQUIER estado (PENDING/APPROVED/REJECTED) — es limpieza operativa del
 * registro de la solicitud, no afecta al `User` que ya se haya creado (la FK
 * `usuario_creado_id` es de la solicitud hacia el usuario, nunca al reves, asi que
 * borrar la solicitud no deja huerfano a nadie). No confirmado con producto si conviene
 * restringir el borrado a estados ya decididos — se documenta como supuesto, no como
 * regla inventada (CLAUDE.MD §0.6).
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

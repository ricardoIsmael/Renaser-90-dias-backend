package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;

/** Para que la app pinte el aviso persistente con los dias que quedan durante la gracia. */
public interface GetAccountDeletionStatusUseCase {

    EstadoBajaCuenta status(UserId userId);
}

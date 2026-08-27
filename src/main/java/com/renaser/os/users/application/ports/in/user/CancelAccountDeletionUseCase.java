package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;

/**
 * Deshace una baja de cuenta pedida por el propio usuario, mientras siga en el plazo de
 * gracia (una vez que el cron purga, ya no hay nada que cancelar). Es el complemento
 * necesario de {@link RequestAccountDeletionUseCase}: la gracia solo protege del
 * arrepentimiento si existe una forma real de volver atras.
 */
public interface CancelAccountDeletionUseCase {

    /** Idempotente: cancelar sin tener una baja pendiente no falla, solo no cambia nada. */
    EstadoBajaCuenta cancel(UserId userId);
}

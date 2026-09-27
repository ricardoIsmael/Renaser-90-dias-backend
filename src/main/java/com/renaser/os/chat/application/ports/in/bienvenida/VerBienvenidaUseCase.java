package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.shared.domain.UserId;

/**
 * La bienvenida para editarla (D-210). Solo ADMIN y ALCHEMIST con la cuenta activa: los demás, 403.
 */
public interface VerBienvenidaUseCase {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException si no es ADMIN/ALCHEMIST activo */
    BienvenidaEditable ver(UserId actorId);
}

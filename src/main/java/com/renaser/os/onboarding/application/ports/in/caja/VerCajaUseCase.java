package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.shared.domain.UserId;

/** El detalle de la caja de un aprendiz, para el Admin (D-219). */
public interface VerCajaUseCase {

    /** @throws java.util.NoSuchElementException si no es un aprendiz (404) */
    DetalleDeCaja ver(UserId actorId, UserId aprendizId);
}

package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.shared.domain.UserId;

/** El estado de la caja en la ficha del aprendiz, para su mentor vigente (D-219). */
public interface CajaDelMentorUseCase {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException si no lo acompaña hoy (403) */
    EstadoCaja estadoDe(UserId actorId, UserId aprendizId);
}

package com.renaser.os.onboarding.infrastructure.adapter.in.rest.grabacionv90;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.EstadoValidacionV90;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;

/** {@code status} usa el mismo vocabulario que el resto del sistema para el patron async: PROCESANDO = "processing". */
public record ValidacionV90Response(EstadoIAv90 status, short attempts, String feedback) {

    public static ValidacionV90Response accepted() {
        return new ValidacionV90Response(EstadoIAv90.PROCESANDO, (short) 0, null);
    }

    public static ValidacionV90Response from(EstadoValidacionV90 e) {
        return new ValidacionV90Response(e.estado(), e.intentosIa(), e.feedbackJson());
    }
}

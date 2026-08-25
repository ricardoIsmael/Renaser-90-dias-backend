package com.renaser.os.onboarding.application.ports.out.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;

/**
 * {@link #guardar} es UPSERT por {@code (usuarioId, fase, eje, indice)} (UNIQUE del
 * baseline) — mismo criterio que {@code SaveRespuestaPort}.
 */
public interface SaveGrabacionV90Port {

    GrabacionV90 guardar(GrabacionV90 grabacion);
}

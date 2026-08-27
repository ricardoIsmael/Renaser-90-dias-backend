package com.renaser.os.onboarding.application.ports.out.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadGrabacionV90Port {

    Optional<GrabacionV90> porId(long id);

    Optional<GrabacionV90> porSlot(UserId usuarioId, String fase, String eje, short indice);

    List<GrabacionV90> todasDeUsuario(UserId usuarioId);

    /** Dashboard admin de onboarding (gap #8): cuantas grabaciones hay en cada estado de validacion IA. */
    long contarPorEstado(EstadoIAv90 estado);
}

package com.renaser.os.onboarding.application.ports.out.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface LoadGrabacionV90Port {

    Optional<GrabacionV90> porId(long id);

    Optional<GrabacionV90> porSlot(UserId usuarioId, String fase, String eje, short indice);

    List<GrabacionV90> todasDeUsuario(UserId usuarioId);
}

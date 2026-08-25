package com.renaser.os.onboarding.application.ports.in.grabacionv90;

import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ListarGrabacionesV90UseCase {

    List<GrabacionV90> listar(UserId usuarioId);
}

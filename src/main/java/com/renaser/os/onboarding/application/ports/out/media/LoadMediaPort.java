package com.renaser.os.onboarding.application.ports.out.media;

import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadMediaPort {

    Optional<MediaOnboarding> porId(long mediaId);

    /** Usado por RegistrarGrabacionV90UseCase para confirmar que la media es del mismo usuario. */
    Optional<MediaOnboarding> porIdYUsuario(long mediaId, UserId usuarioId);
}

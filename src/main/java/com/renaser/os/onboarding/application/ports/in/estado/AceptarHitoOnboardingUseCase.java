package com.renaser.os.onboarding.application.ports.in.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface AceptarHitoOnboardingUseCase {

    EstadoOnboarding aceptar(AceptarHitoCommand command);

    record AceptarHitoCommand(@NotNull UserId usuarioId, @NotNull HitoOnboarding hito) {

        public AceptarHitoCommand {
            SelfValidating.validateConstructorArgs(AceptarHitoCommand.class, usuarioId, hito);
        }
    }
}

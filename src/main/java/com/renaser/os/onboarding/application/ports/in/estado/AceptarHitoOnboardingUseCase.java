package com.renaser.os.onboarding.application.ports.in.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public interface AceptarHitoOnboardingUseCase {

    EstadoOnboarding aceptar(AceptarHitoCommand command);

    /**
     * @param versionDeTerminosId solo para {@link HitoOnboarding#TERMINOS} (spec 027): la versión de los términos que la persona leyó, para dejar constancia de
     *                            ella. {@code null} (los demás hitos y una app anterior) = la vigente de su formación; si ya no es la vigente, es 409.
     */
    record AceptarHitoCommand(@NotNull UserId usuarioId, @NotNull HitoOnboarding hito, UUID versionDeTerminosId) {

        public AceptarHitoCommand {
            SelfValidating.validateConstructorArgs(AceptarHitoCommand.class, usuarioId, hito, versionDeTerminosId);
        }
    }
}

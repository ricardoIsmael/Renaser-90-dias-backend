package com.renaser.os.onboarding.application.ports.in.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.shared.domain.UserId;
import java.util.UUID;

public interface AceptarHitoOnboardingUseCase {

    EstadoOnboarding aceptar(AceptarHitoCommand command);

    /**
     * @param versionDeTerminosId solo para {@link HitoOnboarding#TERMINOS} (spec 027): la versión de los términos que la persona leyó, para dejar constancia de
     *                            ella. {@code null} (una app anterior) = la vigente de su formación; si ya no es la vigente, es 409.
     */
    record AceptarHitoCommand(UserId usuarioId, HitoOnboarding hito, UUID versionDeTerminosId) {

        /** A mano y no con {@code SelfValidating}: con dos constructores, ese ayudante valida el primero que devuelve la reflexión, que no tiene por qué ser el canónico. */
        public AceptarHitoCommand {
            if (usuarioId == null || hito == null) {
                throw new IllegalArgumentException("Faltan la persona o el hito");
            }
        }

        /** Sin versión de términos: lo que mandan los hitos del Pacto y la app anterior. */
        public AceptarHitoCommand(UserId usuarioId, HitoOnboarding hito) {
            this(usuarioId, hito, null);
        }
    }
}

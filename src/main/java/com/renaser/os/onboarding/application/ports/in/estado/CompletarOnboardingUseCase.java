package com.renaser.os.onboarding.application.ports.in.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * Accion EXPLICITA: quien llama decide que el onboarding esta completo. NO hay una regla
 * automatica de "todas las preguntas requeridas respondidas + V90 aprobado" — nadie del
 * equipo la confirmo (CLAUDE.MD §0.6), ver pregunta abierta en docs/MODULO_ONBOARDING.md.
 */
public interface CompletarOnboardingUseCase {

    EstadoOnboarding completar(CompletarOnboardingCommand command);

    record CompletarOnboardingCommand(@NotNull UserId usuarioId) {

        public CompletarOnboardingCommand {
            SelfValidating.validateConstructorArgs(CompletarOnboardingCommand.class, usuarioId);
        }
    }
}

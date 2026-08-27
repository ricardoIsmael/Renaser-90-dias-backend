package com.renaser.os.onboarding.application.ports.out.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadEstadoOnboardingPort {

    Optional<EstadoOnboarding> deUsuario(UserId usuarioId);

    /** Dashboard admin de onboarding (gap #8 de docs/PLAN_INTEGRACION_FRONTEND.md): agregado de `estado_onboarding`. */
    ResumenEstadosOnboarding contarResumen();

    record ResumenEstadosOnboarding(long totalIniciados, long totalCompletados, long totalPactoFirmado) {
    }
}

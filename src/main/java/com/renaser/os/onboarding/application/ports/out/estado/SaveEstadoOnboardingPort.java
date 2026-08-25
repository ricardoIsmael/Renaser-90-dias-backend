package com.renaser.os.onboarding.application.ports.out.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;

public interface SaveEstadoOnboardingPort {

    EstadoOnboarding guardar(EstadoOnboarding estado);
}

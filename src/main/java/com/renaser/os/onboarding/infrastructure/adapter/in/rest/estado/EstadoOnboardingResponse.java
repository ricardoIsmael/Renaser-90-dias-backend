package com.renaser.os.onboarding.infrastructure.adapter.in.rest.estado;

import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;

import java.time.Instant;

public record EstadoOnboardingResponse(String userId, String currentFlow, String currentSection, Integer currentStep,
                                        String flowProgress, Instant termsAcceptedAt, Instant pactAcceptedAt,
                                        Instant pactSignedAt, Instant rocksSyncAcceptedAt, Instant startedAt,
                                        Instant lastActivityAt, boolean completed, Instant completedAt) {

    public static EstadoOnboardingResponse from(EstadoOnboarding e) {
        return new EstadoOnboardingResponse(e.usuarioId().toString(), e.flujoActual(), e.seccionActual(),
                e.pasoActual(), e.progresoFlujo(), e.terminosAceptadosEn(), e.pactoAceptadoEn(), e.pactoFirmadoEn(),
                e.rocasSyncAceptadoEn(), e.iniciadoEn(), e.ultimaActividadEn(), e.completado(), e.completadoEn());
    }
}

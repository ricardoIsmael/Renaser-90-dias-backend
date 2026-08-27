package com.renaser.os.onboarding.infrastructure.adapter.in.rest.admin;

import com.renaser.os.onboarding.application.ports.in.admin.OnboardingDashboardUseCase.ResumenOnboardingAdmin;

/** Proyeccion del dashboard admin de onboarding (gap #8), wire en ingles (mismo criterio que el resto del modulo). */
public record OnboardingDashboardResponse(
        long totalActiveTrainees,
        long totalOnboardingStarted,
        long totalOnboardingCompleted,
        long totalPhase1PactSigned,
        long recordingsPending,
        long recordingsProcessing,
        long recordingsManualReview,
        long recordingsApproved,
        long recordingsRejected) {

    public static OnboardingDashboardResponse from(ResumenOnboardingAdmin resumen) {
        return new OnboardingDashboardResponse(
                resumen.totalAprendicesActivos(),
                resumen.totalOnboardingIniciado(),
                resumen.totalOnboardingCompletado(),
                resumen.totalPactoFase1Firmado(),
                resumen.grabacionesPendientes(),
                resumen.grabacionesProcesando(),
                resumen.grabacionesRevisionManual(),
                resumen.grabacionesAprobadas(),
                resumen.grabacionesRechazadas());
    }
}

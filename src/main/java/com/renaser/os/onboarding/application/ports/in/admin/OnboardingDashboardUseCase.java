package com.renaser.os.onboarding.application.ports.in.admin;

import com.renaser.os.shared.domain.UserId;

/**
 * Dashboard admin de onboarding (gap #8 de docs/PLAN_INTEGRACION_FRONTEND.md): agregado
 * de cuantos aprendices estan en que estado de onboarding, y cuantas grabaciones V90
 * estan pendientes de revision. Solo ADMIN/ALCHEMIST — gate via {@code users.api.UserSummaryFinder}
 * (no via {@code onboarding.application.ports.out.actor.ConsultarActorPort}: ese puerto
 * es compartido por 6 servicios existentes y no expone rol, solo suspension — agregarle
 * un campo hoy tocaria los 6 sin necesidad).
 */
public interface OnboardingDashboardUseCase {

    ResumenOnboardingAdmin obtenerResumen(UserId actorId);

    record ResumenOnboardingAdmin(
            long totalAprendicesActivos,
            long totalOnboardingIniciado,
            long totalOnboardingCompletado,
            long totalPactoFase1Firmado,
            long grabacionesPendientes,
            long grabacionesProcesando,
            long grabacionesRevisionManual,
            long grabacionesAprobadas,
            long grabacionesRechazadas) {

        public ResumenOnboardingAdmin {
            if (totalAprendicesActivos < 0 || totalOnboardingIniciado < 0 || totalOnboardingCompletado < 0
                    || totalPactoFase1Firmado < 0 || grabacionesPendientes < 0 || grabacionesProcesando < 0
                    || grabacionesRevisionManual < 0 || grabacionesAprobadas < 0 || grabacionesRechazadas < 0) {
                throw new IllegalArgumentException("Ningun contador del dashboard puede ser negativo");
            }
        }
    }
}

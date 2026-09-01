package com.renaser.os.onboarding.infrastructure.adapter.in.rest.admin;

import com.renaser.os.onboarding.application.ports.in.admin.OnboardingDashboardUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard admin de onboarding (gap #8 de docs/PLAN_INTEGRACION_FRONTEND.md). Solo
 * ADMIN/ALCHEMIST — gate DENTRO del servicio (CLAUDE.MD §5.4.6). El actor se resuelve
 * desde la sesion, con el header TEMPORAL X-Actor-Id como respaldo mientras dure la
 * migracion — ver ActorAutenticadoArgumentResolver.
 */
@RestController
@RequestMapping("/api/v1/admin/onboarding")
public class OnboardingDashboardController {

    private final OnboardingDashboardUseCase dashboardUseCase;

    public OnboardingDashboardController(OnboardingDashboardUseCase dashboardUseCase) {
        this.dashboardUseCase = dashboardUseCase;
    }

    @RequiresPermission(Permission.VIEW_ONBOARDING_DASHBOARD)
    @GetMapping("/dashboard")
    public OnboardingDashboardResponse dashboard(@ActorAutenticado UserId actor) {
        return OnboardingDashboardResponse.from(dashboardUseCase.obtenerResumen(actor));
    }
}

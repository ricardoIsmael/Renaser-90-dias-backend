package com.renaser.os.onboarding.infrastructure.adapter.in.rest.admin;

import com.renaser.os.onboarding.application.ports.in.admin.OnboardingDashboardUseCase;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard admin de onboarding (gap #8 de docs/PLAN_INTEGRACION_FRONTEND.md). Solo
 * ADMIN/ALCHEMIST — gate DENTRO del servicio (CLAUDE.MD §5.4.6). X-Actor-Id: header
 * TEMPORAL, ver nota de AccountRequestController.
 */
@RestController
@RequestMapping("/api/v1/admin/onboarding")
public class OnboardingDashboardController {

    private final OnboardingDashboardUseCase dashboardUseCase;

    public OnboardingDashboardController(OnboardingDashboardUseCase dashboardUseCase) {
        this.dashboardUseCase = dashboardUseCase;
    }

    @GetMapping("/dashboard")
    public OnboardingDashboardResponse dashboard(@RequestHeader("X-Actor-Id") String actorId) {
        return OnboardingDashboardResponse.from(dashboardUseCase.obtenerResumen(UserId.of(actorId)));
    }
}

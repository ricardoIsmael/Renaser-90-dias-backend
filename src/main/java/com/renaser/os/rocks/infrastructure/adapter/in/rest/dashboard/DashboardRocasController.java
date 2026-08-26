package com.renaser.os.rocks.infrastructure.adapter.in.rest.dashboard;

import com.renaser.os.rocks.application.ports.in.dashboard.ConsultarDashboardRocasUseCase;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Hueco #15: un solo llamado para la pantalla principal de Rocas. */
@RestController
@RequestMapping("/api/v1/rocks")
public class DashboardRocasController {

    private final ConsultarDashboardRocasUseCase dashboardUseCase;

    public DashboardRocasController(ConsultarDashboardRocasUseCase dashboardUseCase) {
        this.dashboardUseCase = dashboardUseCase;
    }

    @GetMapping
    public DashboardRocasResponse dashboard(@RequestHeader("X-Actor-Id") String actorId) {
        return DashboardRocasResponse.from(dashboardUseCase.dashboard(UserId.of(actorId)));
    }
}

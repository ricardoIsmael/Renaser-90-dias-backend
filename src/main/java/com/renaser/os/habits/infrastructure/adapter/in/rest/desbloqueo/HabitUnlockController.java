package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ruta literal del contrato viejo (D-36): {@code GET /api/v1/habit-unlocks}. Solo lectura (D-H2). */
@RestController
@RequestMapping("/api/v1/habit-unlocks")
public class HabitUnlockController {

    private final ConsultarDesbloqueosHabitoUseCase consultarUseCase;

    public HabitUnlockController(ConsultarDesbloqueosHabitoUseCase consultarUseCase) {
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public HabitUnlockPlanResponse consultar(@ActorAutenticado UserId actor) {
        return HabitUnlockPlanResponse.from(consultarUseCase.consultar(actor));
    }
}

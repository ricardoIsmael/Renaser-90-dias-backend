package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ruta literal del contrato viejo (D-36): {@code GET /api/v1/habit-unlocks}. Lectura (D-H2) +
 * alta autoservicio ({@code PUT .../{habitId}}, ver {@link ElegirHabitoUseCase}) — NO es el
 * algoritmo de escalonamiento por lotes del repo viejo, ruta sin verificar 1:1 contra el
 * contrato viejo (misma salvedad que el resto del modulo, docs/MODULO_HABITS.md §3).
 */
@RestController
@RequestMapping("/api/v1/habit-unlocks")
public class HabitUnlockController {

    private final ConsultarDesbloqueosHabitoUseCase consultarUseCase;
    private final ElegirHabitoUseCase elegirUseCase;

    public HabitUnlockController(ConsultarDesbloqueosHabitoUseCase consultarUseCase,
                                  ElegirHabitoUseCase elegirUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.elegirUseCase = elegirUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public HabitUnlockPlanResponse consultar(@ActorAutenticado UserId actor) {
        return HabitUnlockPlanResponse.from(consultarUseCase.consultar(actor));
    }

    /** Idempotente: elegir el mismo habito dos veces no falla ni duplica (ver javadoc del caso de uso). */
    @RequiresPermission(Permission.USE_APP)
    @PutMapping("/{habitId}")
    public HabitUnlockPlanResponse.HabitUnlockItemResponse elegir(@ActorAutenticado UserId actor,
                                                                    @PathVariable UUID habitId) {
        var desbloqueo = elegirUseCase.elegir(new ElegirHabitoCommand(actor, HabitoId.of(habitId)));
        return HabitUnlockPlanResponse.HabitUnlockItemResponse.from(desbloqueo);
    }
}

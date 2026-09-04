package com.renaser.os.habits.infrastructure.adapter.in.rest.desbloqueo;

import com.renaser.os.habits.application.ports.in.desbloqueo.ConsultarDesbloqueosHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase.QuitarHabitoCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase;
    private final QuitarHabitoDelPlanUseCase quitarUseCase;

    public HabitUnlockController(ConsultarDesbloqueosHabitoUseCase consultarUseCase,
                                  ElegirHabitoUseCase elegirUseCase,
                                  CambiarEstadoHabitoDelPlanUseCase cambiarEstadoUseCase,
                                  QuitarHabitoDelPlanUseCase quitarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.elegirUseCase = elegirUseCase;
        this.cambiarEstadoUseCase = cambiarEstadoUseCase;
        this.quitarUseCase = quitarUseCase;
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

    /**
     * D-87: el interruptor ACTIVO/PAUSADO de Plan. Es el endpoint que ese boton nunca tuvo —
     * hasta ahora el cambio vivia solo en el estado de React y se perdia al cerrar la app.
     * Self por construccion: no recibe un id de aprendiz, solo el habito.
     */
    @RequiresPermission(Permission.USE_APP)
    @PatchMapping("/{habitId}")
    public HabitUnlockPlanResponse.HabitUnlockItemResponse cambiarEstado(
            @ActorAutenticado UserId actor, @PathVariable UUID habitId,
            @RequestBody @Valid CambiarEstadoHabitoRequest request) {
        var desbloqueo = cambiarEstadoUseCase.cambiarEstado(
                new CambiarEstadoHabitoCommand(actor, HabitoId.of(habitId), request.active()));
        return HabitUnlockPlanResponse.HabitUnlockItemResponse.from(desbloqueo);
    }

    /** Idempotente: quitar un habito que no esta en el plan tambien devuelve 204. */
    @RequiresPermission(Permission.USE_APP)
    @DeleteMapping("/{habitId}")
    public ResponseEntity<Void> quitar(@ActorAutenticado UserId actor, @PathVariable UUID habitId) {
        quitarUseCase.quitar(new QuitarHabitoCommand(actor, HabitoId.of(habitId)));
        return ResponseEntity.noContent().build();
    }
}

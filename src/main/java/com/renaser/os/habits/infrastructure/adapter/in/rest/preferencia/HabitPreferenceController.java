package com.renaser.os.habits.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Hueco #12 — horario personal de un habito. Ruta literal del contrato viejo (D-36):
 * {@code PATCH /api/v1/habit-preferences/{habitId}}, mas el {@code GET /api/v1/habit-preferences}
 * que faltaba para poder leer la configuracion vigente (E-55). Actor resuelto desde la sesion, con
 * respaldo por el header temporal {@code X-Actor-Id} (D-29).
 */
@RestController
@RequestMapping("/api/v1/habit-preferences")
public class HabitPreferenceController {

    private final EditarPreferenciaHorarioUseCase editarUseCase;
    private final ConsultarPreferenciasHorarioUseCase consultarUseCase;

    public HabitPreferenceController(EditarPreferenciaHorarioUseCase editarUseCase,
                                      ConsultarPreferenciasHorarioUseCase consultarUseCase) {
        this.editarUseCase = editarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public HabitPreferencesResponse consultar(@ActorAutenticado UserId actor) {
        return HabitPreferencesResponse.from(consultarUseCase.consultar(actor));
    }

    @PatchMapping("/{habitId}")
    public HabitPreferenceResponse editar(@ActorAutenticado UserId actor, @PathVariable UUID habitId,
                                           @RequestBody @Valid UpdateHabitPreferenceRequest request) {
        var resultado = editarUseCase.editar(new EditarPreferenciaHorarioCommand(actor,
                HabitoId.of(habitId), request.triggerTime(), request.limitTime(), request.reminderEnabled(),
                request.reminderMinutesBefore()));
        return HabitPreferenceResponse.from(resultado);
    }
}

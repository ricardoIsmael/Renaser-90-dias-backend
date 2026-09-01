package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.application.ports.in.guiaadmin.ConsultarGuiasDeHabitoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarGuiaHabitoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarGuiaHabitoUseCase.EliminarGuiaHabitoCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.UpsertGuiaHabitoUseCase;
import com.renaser.os.habits.application.ports.in.guiaadmin.UpsertGuiaHabitoUseCase.UpsertGuiaHabitoCommand;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Panel admin de guias del catalogo (hueco #11). Rutas literales de {@code habitsAdmin.ts}. */
@RestController
@RequestMapping("/api/v1/admin/habits")
public class GuiaHabitoAdminController {

    private final ConsultarGuiasDeHabitoUseCase consultarUseCase;
    private final UpsertGuiaHabitoUseCase upsertUseCase;
    private final EliminarGuiaHabitoUseCase eliminarUseCase;

    public GuiaHabitoAdminController(ConsultarGuiasDeHabitoUseCase consultarUseCase,
                                      UpsertGuiaHabitoUseCase upsertUseCase, EliminarGuiaHabitoUseCase eliminarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.upsertUseCase = upsertUseCase;
        this.eliminarUseCase = eliminarUseCase;
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @GetMapping("/{habitId}/guides")
    public List<HabitGuideResponse> listar(@ActorAutenticado UserId actor, @PathVariable UUID habitId) {
        return consultarUseCase.listar(actor, HabitoId.of(habitId)).stream()
                .map(HabitGuideResponse::from).toList();
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PostMapping("/{habitId}/guides")
    public ResponseEntity<HabitGuideResponse> upsert(@ActorAutenticado UserId actor,
                                                       @PathVariable UUID habitId,
                                                       @RequestBody @Valid UpsertGuideRequest request) {
        var conAdjuntos = upsertUseCase.upsert(new UpsertGuiaHabitoCommand(actor, HabitoId.of(habitId),
                request.startDay(), request.endDay(), request.toContenido(), request.closePrevious()));
        return ResponseEntity.status(HttpStatus.CREATED).body(HabitGuideResponse.from(conAdjuntos));
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @DeleteMapping("/guides/{guideId}")
    public ResponseEntity<Void> eliminar(@ActorAutenticado UserId actor, @PathVariable UUID guideId) {
        eliminarUseCase.eliminar(new EliminarGuiaHabitoCommand(actor, GuiaHabitoId.of(guideId)));
        return ResponseEntity.noContent().build();
    }
}

package com.renaser.os.habits.infrastructure.adapter.in.rest.horarioadmin;

import com.renaser.os.habits.application.ports.in.horarioadmin.ActualizarHorarioHabitoUseCase;
import com.renaser.os.habits.application.ports.in.horarioadmin.ActualizarHorarioHabitoUseCase.ActualizarHorarioHabitoCommand;
import com.renaser.os.habits.application.ports.in.horarioadmin.ConsultarHorariosDeHabitoUseCase;
import com.renaser.os.habits.application.ports.in.horarioadmin.CrearHorarioHabitoUseCase;
import com.renaser.os.habits.application.ports.in.horarioadmin.CrearHorarioHabitoUseCase.CrearHorarioHabitoCommand;
import com.renaser.os.habits.application.ports.in.horarioadmin.EliminarHorarioHabitoUseCase;
import com.renaser.os.habits.application.ports.in.horarioadmin.EliminarHorarioHabitoUseCase.EliminarHorarioHabitoCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
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
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/** Panel admin de horarios por defecto del catalogo (hueco #11). Rutas literales de {@code habitsAdmin.ts}. */
@RestController
@RequestMapping("/api/v1/admin/habits")
public class HorarioHabitoAdminController {

    private final ConsultarHorariosDeHabitoUseCase consultarUseCase;
    private final CrearHorarioHabitoUseCase crearUseCase;
    private final ActualizarHorarioHabitoUseCase actualizarUseCase;
    private final EliminarHorarioHabitoUseCase eliminarUseCase;

    public HorarioHabitoAdminController(ConsultarHorariosDeHabitoUseCase consultarUseCase,
                                         CrearHorarioHabitoUseCase crearUseCase,
                                         ActualizarHorarioHabitoUseCase actualizarUseCase,
                                         EliminarHorarioHabitoUseCase eliminarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.crearUseCase = crearUseCase;
        this.actualizarUseCase = actualizarUseCase;
        this.eliminarUseCase = eliminarUseCase;
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @GetMapping("/{habitId}/schedules")
    public List<HabitScheduleResponse> listar(@ActorAutenticado UserId actor,
                                               @PathVariable UUID habitId) {
        return consultarUseCase.listar(actor, HabitoId.of(habitId)).stream()
                .map(HabitScheduleResponse::from).toList();
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PostMapping("/{habitId}/schedules")
    public ResponseEntity<HabitScheduleResponse> crear(@ActorAutenticado UserId actor,
                                                         @PathVariable UUID habitId,
                                                         @RequestBody @Valid CreateScheduleRequest request) {
        var horario = crearUseCase.crear(new CrearHorarioHabitoCommand(actor, HabitoId.of(habitId),
                request.startDay(), request.endDay(), request.dayType().toDomain(), request.defaultTriggerTime(),
                request.defaultLimitTime()));
        return ResponseEntity.status(HttpStatus.CREATED).body(HabitScheduleResponse.from(horario));
    }

    /**
     * {@code UpdateScheduleInput} es un PATCH real: una clave AUSENTE del JSON deja el
     * campo como esta, una clave presente con {@code null} lo limpia (ej. "quitar
     * endDay" = volver el horario abierto). Un record normal no distingue "ausente" de
     * "null explicito" — por eso el body se lee como {@link JsonNode} y esta es la UNICA
     * excepcion al mapeo a mano con DTOs tipados del resto de este controller.
     */
    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @PostMapping("/schedules/{scheduleId}")
    public HabitScheduleResponse actualizar(@ActorAutenticado UserId actor,
                                             @PathVariable UUID scheduleId, @RequestBody JsonNode body) {
        var request = PartialUpdateScheduleRequest.from(body);
        var dayType = request.dayType() != null ? request.dayType().toDomain() : null;
        var horario = actualizarUseCase.actualizar(new ActualizarHorarioHabitoCommand(actor,
                HorarioHabitoId.of(scheduleId), request.startDay(), request.endDay(), dayType,
                request.defaultTriggerTime(), request.defaultLimitTime(), request.limpiarEndDay(),
                request.limpiarHoraDisparo(), request.limpiarHoraLimite()));
        return HabitScheduleResponse.from(horario);
    }

    @RequiresPermission(Permission.MANAGE_HABIT_CATALOG)
    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<Void> eliminar(@ActorAutenticado UserId actor, @PathVariable UUID scheduleId) {
        eliminarUseCase.eliminar(new EliminarHorarioHabitoCommand(actor, HorarioHabitoId.of(scheduleId)));
        return ResponseEntity.noContent().build();
    }
}

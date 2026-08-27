package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase.ActualizarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase.CambiarActivoHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.ConsultarCatalogoAdminUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase.CrearHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase.EliminarHabitoCommand;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
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

/**
 * Panel admin de catalogo de habitos (hueco #11, docs/PLAN_INTEGRACION_FRONTEND.md #11).
 * Rutas literales del contrato ya escrito por el cliente ({@code habitsAdmin.ts}). Solo
 * ADMIN/ALCHEMIST (gateado en el servicio, {@code HabitoAdminGuard}) — el actor lo resuelve
 * {@code @ActorAutenticado} desde la sesion propia, con respaldo por el header temporal
 * {@code X-Actor-Id} mientras dure la migracion; el cliente hoy manda {@code Authorization:
 * Bearer} (JWT de Supabase), que este backend no valida — deuda ya conocida, no nueva.
 */
@RestController
@RequestMapping("/api/v1/admin/habits")
public class HabitoAdminController {

    private final ConsultarCatalogoAdminUseCase consultarUseCase;
    private final CrearHabitoUseCase crearUseCase;
    private final ActualizarHabitoUseCase actualizarUseCase;
    private final CambiarActivoHabitoUseCase cambiarActivoUseCase;
    private final EliminarHabitoUseCase eliminarUseCase;

    public HabitoAdminController(ConsultarCatalogoAdminUseCase consultarUseCase, CrearHabitoUseCase crearUseCase,
                                  ActualizarHabitoUseCase actualizarUseCase,
                                  CambiarActivoHabitoUseCase cambiarActivoUseCase,
                                  EliminarHabitoUseCase eliminarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.crearUseCase = crearUseCase;
        this.actualizarUseCase = actualizarUseCase;
        this.cambiarActivoUseCase = cambiarActivoUseCase;
        this.eliminarUseCase = eliminarUseCase;
    }

    @GetMapping
    public List<AdminHabitResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.listar(actor).stream().map(AdminHabitResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<AdminHabitResponse> crear(@ActorAutenticado UserId actor,
                                                      @RequestBody @Valid CreateHabitRequest request) {
        var habito = crearUseCase.crear(new CrearHabitoCommand(actor, request.title(),
                request.habitType().toDomain(), request.toDetalles()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminHabitResponse.from(habito));
    }

    @PostMapping("/{id}")
    public AdminHabitResponse actualizar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                          @RequestBody @Valid UpdateHabitRequest request) {
        var habito = actualizarUseCase.actualizar(new ActualizarHabitoCommand(actor, HabitoId.of(id),
                request.toDetalles()));
        return AdminHabitResponse.from(habito);
    }

    @PostMapping("/{id}/toggle")
    public AdminHabitResponse toggle(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                      @RequestBody ToggleHabitRequest request) {
        var habito = cambiarActivoUseCase.cambiarActivo(new CambiarActivoHabitoCommand(actor,
                HabitoId.of(id), request.isActive()));
        return AdminHabitResponse.from(habito);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        eliminarUseCase.eliminar(new EliminarHabitoCommand(actor, HabitoId.of(id)));
        return ResponseEntity.noContent().build();
    }
}

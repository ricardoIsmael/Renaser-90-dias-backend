package com.renaser.os.community.infrastructure.adapter.in.rest.cohorte;

import com.renaser.os.community.application.ports.in.cohorte.ActualizarCohorteUseCase;
import com.renaser.os.community.application.ports.in.cohorte.ActualizarCohorteUseCase.ActualizarCohorteCommand;
import com.renaser.os.community.application.ports.in.cohorte.CambiarEstadoCohorteUseCase;
import com.renaser.os.community.application.ports.in.cohorte.CambiarEstadoCohorteUseCase.CambiarEstadoCohorteCommand;
import com.renaser.os.community.application.ports.in.cohorte.ConsultarCohortesUseCase;
import com.renaser.os.community.application.ports.in.cohorte.CrearCohorteUseCase;
import com.renaser.os.community.application.ports.in.cohorte.CrearCohorteUseCase.CrearCohorteCommand;
import com.renaser.os.community.application.ports.in.cohorte.EliminarCohorteUseCase;
import com.renaser.os.community.application.ports.in.cohorte.EliminarCohorteUseCase.EliminarCohorteCommand;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/cohorts")
public class CohorteAdminController {

    private final CrearCohorteUseCase crearUseCase;
    private final ActualizarCohorteUseCase actualizarUseCase;
    private final CambiarEstadoCohorteUseCase cambiarEstadoUseCase;
    private final EliminarCohorteUseCase eliminarUseCase;
    private final ConsultarCohortesUseCase consultarUseCase;

    public CohorteAdminController(CrearCohorteUseCase crearUseCase, ActualizarCohorteUseCase actualizarUseCase,
                                   CambiarEstadoCohorteUseCase cambiarEstadoUseCase,
                                   EliminarCohorteUseCase eliminarUseCase, ConsultarCohortesUseCase consultarUseCase) {
        this.crearUseCase = crearUseCase;
        this.actualizarUseCase = actualizarUseCase;
        this.cambiarEstadoUseCase = cambiarEstadoUseCase;
        this.eliminarUseCase = eliminarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @RequiresPermission(value = Permission.MANAGE_COHORTS, scope = "un MENTOR pasa igual pero solo ve su cohorte")
    @GetMapping
    public List<CohorteResponse> listar(@ActorAutenticado UserId actorId,
                                         @RequestParam(required = false) String status) {
        EstadoCohorte filtro = status == null ? null : parseEstado(status);
        return consultarUseCase.listar(actorId, filtro).stream().map(CohorteResponse::from).toList();
    }

    @RequiresPermission(value = Permission.MANAGE_COHORTS, scope = "un MENTOR pasa igual, pero solo sobre la cohorte de la celula que lidera")
    @GetMapping("/{id}")
    public CohorteResponse obtener(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        return CohorteResponse.from(consultarUseCase.obtener(actorId, CohorteId.of(id)));
    }

    @RequiresPermission(Permission.MANAGE_COHORTS)
    @PostMapping
    public ResponseEntity<CohorteResponse> crear(@ActorAutenticado UserId actorId,
                                                  @RequestBody @Valid CrearCohorteRequest request) {
        var resumen = crearUseCase.crear(new CrearCohorteCommand(actorId, request.name(),
                request.startDate(), request.endDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(CohorteResponse.from(resumen));
    }

    @RequiresPermission(Permission.MANAGE_COHORTS)
    @PatchMapping("/{id}")
    public CohorteResponse actualizar(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                       @RequestBody ActualizarCohorteRequest request) {
        return CohorteResponse.from(actualizarUseCase.actualizar(new ActualizarCohorteCommand(actorId,
                CohorteId.of(id), request.name(), request.startDate(), request.endDate(), true)));
    }

    @RequiresPermission(Permission.MANAGE_COHORTS)
    @PatchMapping("/{id}/status")
    public CohorteResponse cambiarEstado(@ActorAutenticado UserId actorId, @PathVariable UUID id,
                                          @RequestBody @Valid CambiarEstadoCohorteRequest request) {
        return CohorteResponse.from(cambiarEstadoUseCase.cambiarEstado(new CambiarEstadoCohorteCommand(actorId,
                CohorteId.of(id), parseEstado(request.status()))));
    }

    @RequiresPermission(Permission.MANAGE_COHORTS)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        eliminarUseCase.eliminar(new EliminarCohorteCommand(actorId, CohorteId.of(id)));
        return ResponseEntity.noContent().build();
    }

    private static EstadoCohorte parseEstado(String status) {
        return switch (status) {
            case "PLANNED" -> EstadoCohorte.PLANIFICADA;
            case "ACTIVE" -> EstadoCohorte.ACTIVA;
            case "COMPLETED" -> EstadoCohorte.COMPLETADA;
            default -> throw new IllegalArgumentException("status invalido: " + status);
        };
    }
}

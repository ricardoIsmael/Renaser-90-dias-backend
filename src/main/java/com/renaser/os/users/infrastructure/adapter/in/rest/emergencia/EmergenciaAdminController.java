package com.renaser.os.users.infrastructure.adapter.in.rest.emergencia;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.emergencia.AtenderEmergenciaUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Los pedidos de emergencia para quien atiende soporte (D-244): ADMIN/ALCHEMIST, los mismos que están en cada
 * chat de soporte. Aplicar el día pedido es «Cambiar día del programa» ({@code PUT /admin/trainees/{id}/program-day}),
 * que además deja el pedido resuelto; acá solo se lee y se cierra sin cambiar el día.
 */
@RestController
public class EmergenciaAdminController {

    private final AtenderEmergenciaUseCase atender;

    public EmergenciaAdminController(AtenderEmergenciaUseCase atender) {
        this.atender = atender;
    }

    /** 200 con el pedido abierto, o 204 si esa persona no tiene ninguno. */
    @RequiresPermission(Permission.MANAGE_TRAINEES)
    @GetMapping("/api/v1/admin/trainees/{id}/emergency-request")
    public ResponseEntity<EmergenciaParaSoporteResponse> abierta(@PathVariable UUID id, @ActorAutenticado UserId actor) {
        return atender.abiertaDe(actor, UserId.of(id))
                .map(EmergenciaParaSoporteResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @RequiresPermission(Permission.MANAGE_TRAINEES)
    @PostMapping("/api/v1/admin/emergency-requests/{id}/close")
    public EmergenciaResponse cerrar(@PathVariable UUID id, @ActorAutenticado UserId actor) {
        return EmergenciaResponse.from(atender.cerrarSinCambio(actor, id));
    }
}

package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.eliminacion.AdministrarEliminacionDeCuentasUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Administracion → persona → «Eliminar cuenta» / «Recuperar cuenta» (D-243). Solo ADMIN y ALQUIMISTA
 * activos; las reglas finas (a un ADMIN/ALQUIMISTA solo lo elimina un ADMIN, nadie a si mismo) las
 * impone el dominio ({@code ReglaDeEliminacionPorAdmin}).
 *
 * <p>{@code POST} y no {@code DELETE}: lleva cuerpo (el correo escrito como confirmacion), y un
 * cuerpo en un DELETE no todos los clientes y proxys lo respetan.
 */
@RestController
@RequestMapping("/api/v1/admin/users/{id}/account-deletion")
public class EliminacionDeCuentasAdminController {

    private final AdministrarEliminacionDeCuentasUseCase administrar;

    public EliminacionDeCuentasAdminController(AdministrarEliminacionDeCuentasUseCase administrar) {
        this.administrar = administrar;
    }

    @RequiresPermission(value = Permission.MANAGE_STAFF,
            scope = "el guard real es ReglaDeEliminacionPorAdmin: ADMIN/ALCHEMIST activos, nunca sobre si mismo")
    @PostMapping
    public ResponseEntity<Void> eliminar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                         @RequestBody @Valid EliminarCuentaRequest request) {
        administrar.eliminar(actor, UserId.of(id), request.confirmEmail());
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(Permission.MANAGE_STAFF)
    @PostMapping("/recover")
    public ResponseEntity<Void> recuperar(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        administrar.recuperar(actor, UserId.of(id));
        return ResponseEntity.noContent().build();
    }
}

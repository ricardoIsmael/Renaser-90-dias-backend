package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.admin.ConsultarRolesAsignablesUseCase;
import com.renaser.os.users.application.ports.in.admin.ConsultarRolesAsignablesUseCase.RolAsignable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/admin/roles-asignables} (spec 002, fase 2; contrato §4.5; D-272): la lista que ofrecen «Equipo y
 * roles» y Solicitudes. Solo ADMIN y ALQUIMISTA: el guard real es {@code RequireAdminGuard} en el servicio (la matriz
 * falla abierta para MENTOR, A-1). Va con {@code admin} y no con {@code admin.equipo}: la usan las dos pantallas.
 * {@code GET /admin/habits/roles} no cambia (decisión del dueño, P3).
 */
@RestController
@RequestMapping("/api/v1/admin/roles-asignables")
@Funcionalidad("admin")
public class RolesAsignablesController {

    private final ConsultarRolesAsignablesUseCase consultar;

    public RolesAsignablesController(ConsultarRolesAsignablesUseCase consultar) {
        this.consultar = consultar;
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es RequireAdminGuard: ADMIN/ALCHEMIST activos")
    @GetMapping
    public List<RolAsignableResponse> listar(@ActorAutenticado UserId actor) {
        return consultar.rolesAsignables(actor).stream().map(RolAsignableResponse::from).toList();
    }

    /** {@code { clave, nombre, rolBase, rolPersonalizadoId }}; {@code rolPersonalizadoId} es {@code null} en los base. */
    public record RolAsignableResponse(String clave, String nombre, UserRole rolBase, UUID rolPersonalizadoId) {

        static RolAsignableResponse from(RolAsignable rol) {
            return new RolAsignableResponse(rol.clave(), rol.nombre(), rol.rolBase(), rol.rolPersonalizadoId());
        }
    }
}

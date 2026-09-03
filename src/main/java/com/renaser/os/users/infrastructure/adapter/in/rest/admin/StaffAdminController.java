package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.admin.ListStaffUseCase;
import com.renaser.os.users.application.ports.in.admin.ListStaffUseCase.ListStaffCommand;
import com.renaser.os.users.application.ports.in.admin.UpdateStaffProfileUseCase;
import com.renaser.os.users.application.ports.in.admin.UpdateStaffProfileUseCase.UpdateStaffProfileCommand;
import com.renaser.os.users.application.ports.in.admin.UpdateUserStatusUseCase;
import com.renaser.os.users.application.ports.in.admin.UpdateUserStatusUseCase.UpdateUserStatusCommand;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase.InviteStaffCommand;
import com.renaser.os.users.infrastructure.adapter.in.rest.user.UserIdResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Panel admin de staff (gap #6 de docs/PLAN_INTEGRACION_FRONTEND.md): listar, invitar con
 * contrasena temporal, cambiar estado, editar a otro usuario. Solo ADMIN/ALCHEMIST — gate
 * DENTRO del servicio ({@code RequireAdminGuard}), nunca en el controller (CLAUDE.MD §5.4.6).
 *
 * <p>Actor: {@code @ActorAutenticado} lo toma de la sesion propia de docs/MODULO_AUTH.md, con
 * respaldo temporal por el header {@code X-Actor-Id} (ver nota de AccountRequestController).
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
public class StaffAdminController {

    private final ListStaffUseCase listStaffUseCase;
    private final InviteAndCreateUserUseCase inviteUseCase;
    private final UpdateUserStatusUseCase updateStatusUseCase;
    private final UpdateStaffProfileUseCase updateStaffProfileUseCase;

    public StaffAdminController(ListStaffUseCase listStaffUseCase, InviteAndCreateUserUseCase inviteUseCase,
                                 UpdateUserStatusUseCase updateStatusUseCase,
                                 UpdateStaffProfileUseCase updateStaffProfileUseCase) {
        this.listStaffUseCase = listStaffUseCase;
        this.inviteUseCase = inviteUseCase;
        this.updateStatusUseCase = updateStatusUseCase;
        this.updateStaffProfileUseCase = updateStaffProfileUseCase;
    }

    @RequiresPermission(Permission.MANAGE_STAFF)
    @GetMapping
    public StaffPageResponse listar(@ActorAutenticado UserId actor,
                                     @RequestParam(required = false) UserRole role,
                                     @RequestParam(required = false) com.renaser.os.users.api.UserStatus status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        var pagina = listStaffUseCase.listar(new ListStaffCommand(actor, role, status, page, size));
        return StaffPageResponse.from(pagina);
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es User.requireRoleManager: invitar es asignar un rol")
    @PostMapping("/invite")
    public ResponseEntity<UserIdResponse> invite(@ActorAutenticado UserId actor,
                                                  @RequestBody @Valid InviteStaffRequest request) {
        UserId invited = inviteUseCase.inviteStaff(new InviteStaffCommand(request.email(), request.fullName(),
                request.role(), actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserIdResponse(invited.value()));
    }

    @RequiresPermission(Permission.MANAGE_STAFF)
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                              @RequestBody @Valid UpdateUserStatusRequest request) {
        updateStatusUseCase.updateStatus(new UpdateUserStatusCommand(actor, UserId.of(id),
                request.status()));
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(Permission.MANAGE_STAFF)
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateProfile(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                               @RequestBody UpdateStaffProfileRequest request) {
        updateStaffProfileUseCase.updateStaffProfile(new UpdateStaffProfileCommand(actor, UserId.of(id),
                request.fullName(), request.avatarUrl(), request.bio(), request.department()));
        return ResponseEntity.noContent().build();
    }
}

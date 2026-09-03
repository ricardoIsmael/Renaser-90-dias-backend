package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.user.CancelAccountDeletionUseCase;
import com.renaser.os.users.application.ports.in.user.ConfirmarAvatarUseCase;
import com.renaser.os.users.application.ports.in.user.ConfirmarAvatarUseCase.ConfirmarAvatarCommand;
import com.renaser.os.users.application.ports.in.user.GetAccountDeletionStatusUseCase;
import com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase.InviteUserCommand;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase.RequestAccountDeletionCommand;
import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase;
import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase.SolicitarUrlAvatarCommand;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase.UpdateMyProfileCommand;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase.UpdateUserRoleCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Actor: ver nota de AccountRequestController — se resuelve desde la sesion, con respaldo
 * temporal por el header {@code X-Actor-Id} mientras dura la migracion. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final GetMyFullProfileUseCase getMyFullProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final InviteAndCreateUserUseCase inviteUseCase;
    private final UpdateUserRoleUseCase updateUserRoleUseCase;
    private final SolicitarUrlAvatarUseCase solicitarUrlAvatarUseCase;
    private final ConfirmarAvatarUseCase confirmarAvatarUseCase;
    private final RequestAccountDeletionUseCase requestAccountDeletionUseCase;
    private final CancelAccountDeletionUseCase cancelAccountDeletionUseCase;
    private final GetAccountDeletionStatusUseCase getAccountDeletionStatusUseCase;

    public UserController(GetMyFullProfileUseCase getMyFullProfileUseCase,
                           UpdateMyProfileUseCase updateMyProfileUseCase,
                           InviteAndCreateUserUseCase inviteUseCase, UpdateUserRoleUseCase updateUserRoleUseCase,
                           SolicitarUrlAvatarUseCase solicitarUrlAvatarUseCase,
                           ConfirmarAvatarUseCase confirmarAvatarUseCase,
                           RequestAccountDeletionUseCase requestAccountDeletionUseCase,
                           CancelAccountDeletionUseCase cancelAccountDeletionUseCase,
                           GetAccountDeletionStatusUseCase getAccountDeletionStatusUseCase) {
        this.getMyFullProfileUseCase = getMyFullProfileUseCase;
        this.updateMyProfileUseCase = updateMyProfileUseCase;
        this.inviteUseCase = inviteUseCase;
        this.updateUserRoleUseCase = updateUserRoleUseCase;
        this.solicitarUrlAvatarUseCase = solicitarUrlAvatarUseCase;
        this.confirmarAvatarUseCase = confirmarAvatarUseCase;
        this.requestAccountDeletionUseCase = requestAccountDeletionUseCase;
        this.cancelAccountDeletionUseCase = cancelAccountDeletionUseCase;
        this.getAccountDeletionStatusUseCase = getAccountDeletionStatusUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PostMapping("/me")
    public UserResponse me(@ActorAutenticado UserId actor) {
        return UserResponse.from(getMyFullProfileUseCase.getMyFullProfile(actor));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PatchMapping("/me")
    public ResponseEntity<Void> updateMe(@ActorAutenticado UserId actor,
                                          @RequestBody UpdateMyProfileRequest request) {
        updateMyProfileUseCase.updateMyProfile(new UpdateMyProfileCommand(actor, request.fullName(),
                request.avatarUrl(), request.bio(), request.department()));
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(value = Permission.MANAGE_ROLES, scope = "el guard real es User.requireRoleManager: invitar es asignar un rol")
    @PostMapping("/invite")
    public ResponseEntity<UserIdResponse> invite(@ActorAutenticado UserId actor,
                                                  @RequestBody @Valid InviteUserRequest request) {
        UserId invited = inviteUseCase.invite(new InviteUserCommand(request.usuarioId(), request.email(),
                request.fullName(), request.role(), actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserIdResponse(invited.value()));
    }

    @RequiresPermission(Permission.MANAGE_ROLES)
    @PatchMapping("/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                            @RequestBody @Valid UpdateUserRoleRequest request) {
        updateUserRoleUseCase.updateRole(new UpdateUserRoleCommand(UserId.of(id), request.newRole(), actor));
        return ResponseEntity.noContent().build();
    }

    // ─── Avatar generico (gap #4) — mismo patron upload-url -> PUT -> confirmar
    // que ya usan `rocks`/`onboarding`/`calendar` (docs/PLAN_INTEGRACION_FRONTEND.md §2) ──

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PostMapping("/me/avatar/upload-url")
    public UrlAvatarResponse urlDeSubidaAvatar(@ActorAutenticado UserId actor,
                                                @RequestBody @Valid SolicitarUrlAvatarRequest request) {
        var url = solicitarUrlAvatarUseCase.solicitarUrl(
                new SolicitarUrlAvatarCommand(actor, request.tipoContenido()));
        return UrlAvatarResponse.from(url);
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PatchMapping("/me/avatar")
    public ResponseEntity<Void> confirmarAvatar(@ActorAutenticado UserId actor,
                                                 @RequestBody @Valid ConfirmarAvatarRequest request) {
        confirmarAvatarUseCase.confirmar(
                new ConfirmarAvatarCommand(actor, request.bucket(), request.ruta()));
        return ResponseEntity.noContent().build();
    }

    // ─── Baja de cuenta autogestionada (gap #5) — mismo patron GET/POST/DELETE
    // que /api/v1/mentor/activate-tracking (D-34) ──────────────────────────────

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @GetMapping("/me/account-deletion")
    public AccountDeletionStatusResponse estadoBajaCuenta(@ActorAutenticado UserId actor) {
        return AccountDeletionStatusResponse.from(getAccountDeletionStatusUseCase.status(actor));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PostMapping("/me/account-deletion")
    public AccountDeletionStatusResponse solicitarBajaCuenta(@ActorAutenticado UserId actor,
                                                              @RequestBody @Valid RequestAccountDeletionRequest request) {
        var estado = requestAccountDeletionUseCase.request(
                new RequestAccountDeletionCommand(actor, request.confirmacion()));
        return AccountDeletionStatusResponse.from(estado);
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @DeleteMapping("/me/account-deletion")
    public AccountDeletionStatusResponse cancelarBajaCuenta(@ActorAutenticado UserId actor) {
        return AccountDeletionStatusResponse.from(cancelAccountDeletionUseCase.cancel(actor));
    }
}

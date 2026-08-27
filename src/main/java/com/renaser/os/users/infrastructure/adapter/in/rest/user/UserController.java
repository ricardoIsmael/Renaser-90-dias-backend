package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.shared.domain.UserId;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** X-Actor-Id: ver nota de AccountRequestController — TEMPORAL, no usar en produccion. */
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

    @PostMapping("/me")
    public UserResponse me(@RequestHeader("X-Actor-Id") String actorId) {
        return UserResponse.from(getMyFullProfileUseCase.getMyFullProfile(UserId.of(actorId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateMe(@RequestHeader("X-Actor-Id") String actorId,
                                          @RequestBody UpdateMyProfileRequest request) {
        updateMyProfileUseCase.updateMyProfile(new UpdateMyProfileCommand(UserId.of(actorId), request.fullName(),
                request.avatarUrl(), request.bio(), request.department()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite")
    public ResponseEntity<UserIdResponse> invite(@RequestHeader("X-Actor-Id") String actorId,
                                                  @RequestBody @Valid InviteUserRequest request) {
        UserId invited = inviteUseCase.invite(new InviteUserCommand(request.supabaseUserId(), request.email(),
                request.fullName(), request.role(), UserId.of(actorId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserIdResponse(invited.value()));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<Void> updateRole(@PathVariable UUID id, @RequestHeader("X-Actor-Id") String actorId,
                                            @RequestBody @Valid UpdateUserRoleRequest request) {
        updateUserRoleUseCase.updateRole(new UpdateUserRoleCommand(UserId.of(id), request.newRole(),
                UserId.of(actorId)));
        return ResponseEntity.noContent().build();
    }

    // ─── Avatar generico (gap #4) — mismo patron upload-url -> PUT -> confirmar
    // que ya usan `rocks`/`onboarding`/`calendar` (docs/PLAN_INTEGRACION_FRONTEND.md §2) ──

    @PostMapping("/me/avatar/upload-url")
    public UrlAvatarResponse urlDeSubidaAvatar(@RequestHeader("X-Actor-Id") String actorId,
                                                @RequestBody @Valid SolicitarUrlAvatarRequest request) {
        var url = solicitarUrlAvatarUseCase.solicitarUrl(
                new SolicitarUrlAvatarCommand(UserId.of(actorId), request.tipoContenido()));
        return UrlAvatarResponse.from(url);
    }

    @PatchMapping("/me/avatar")
    public ResponseEntity<Void> confirmarAvatar(@RequestHeader("X-Actor-Id") String actorId,
                                                 @RequestBody @Valid ConfirmarAvatarRequest request) {
        confirmarAvatarUseCase.confirmar(
                new ConfirmarAvatarCommand(UserId.of(actorId), request.bucket(), request.ruta()));
        return ResponseEntity.noContent().build();
    }

    // ─── Baja de cuenta autogestionada (gap #5) — mismo patron GET/POST/DELETE
    // que /api/v1/mentor/activate-tracking (D-34) ──────────────────────────────

    @GetMapping("/me/account-deletion")
    public AccountDeletionStatusResponse estadoBajaCuenta(@RequestHeader("X-Actor-Id") String actorId) {
        return AccountDeletionStatusResponse.from(getAccountDeletionStatusUseCase.status(UserId.of(actorId)));
    }

    @PostMapping("/me/account-deletion")
    public AccountDeletionStatusResponse solicitarBajaCuenta(@RequestHeader("X-Actor-Id") String actorId,
                                                              @RequestBody @Valid RequestAccountDeletionRequest request) {
        var estado = requestAccountDeletionUseCase.request(
                new RequestAccountDeletionCommand(UserId.of(actorId), request.confirmacion()));
        return AccountDeletionStatusResponse.from(estado);
    }

    @DeleteMapping("/me/account-deletion")
    public AccountDeletionStatusResponse cancelarBajaCuenta(@RequestHeader("X-Actor-Id") String actorId) {
        return AccountDeletionStatusResponse.from(cancelAccountDeletionUseCase.cancel(UserId.of(actorId)));
    }
}

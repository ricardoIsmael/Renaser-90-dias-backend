package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase.InviteUserCommand;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase.UpdateMyProfileCommand;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase.UpdateUserRoleCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateMyProfileUseCase updateMyProfileUseCase;
    private final InviteAndCreateUserUseCase inviteUseCase;
    private final UpdateUserRoleUseCase updateUserRoleUseCase;

    public UserController(GetMyProfileUseCase getMyProfileUseCase, UpdateMyProfileUseCase updateMyProfileUseCase,
                           InviteAndCreateUserUseCase inviteUseCase, UpdateUserRoleUseCase updateUserRoleUseCase) {
        this.getMyProfileUseCase = getMyProfileUseCase;
        this.updateMyProfileUseCase = updateMyProfileUseCase;
        this.inviteUseCase = inviteUseCase;
        this.updateUserRoleUseCase = updateUserRoleUseCase;
    }

    @PostMapping("/me")
    public UserResponse me(@RequestHeader("X-Actor-Id") String actorId) {
        return UserResponse.from(getMyProfileUseCase.getMyProfile(UserId.of(actorId)));
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
}

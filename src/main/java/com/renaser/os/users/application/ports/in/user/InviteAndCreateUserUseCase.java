package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Via para MENTOR/ADMIN/ALCHEMIST y cohortes a mitad de programa (CLAUDE.MD §4.3). */
public interface InviteAndCreateUserUseCase {

    UserId invite(InviteUserCommand command);

    record InviteUserCommand(
            @NotBlank String supabaseUserId,
            @NotBlank @Email String email,
            @NotBlank String fullName,
            @NotNull UserRole role,
            @NotNull UserId actorId) {

        public InviteUserCommand {
            SelfValidating.validateConstructorArgs(InviteUserCommand.class,
                    supabaseUserId, email, fullName, role, actorId);
        }
    }
}

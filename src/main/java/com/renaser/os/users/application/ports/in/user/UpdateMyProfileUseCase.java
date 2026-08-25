package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * U-02/U-03. Campos null = "no cambiar" (semantica de PATCH parcial); el comando NO
 * tiene programDay/coherenceScore/leaguePoints/currentPhase/role — el compilador los
 * excluye (§5.3.3).
 */
public interface UpdateMyProfileUseCase {

    void updateMyProfile(UpdateMyProfileCommand command);

    record UpdateMyProfileCommand(
            @NotNull UserId userId,
            String fullName,
            String avatarUrl,
            String bio,
            String department) {

        public UpdateMyProfileCommand {
            SelfValidating.validateConstructorArgs(UpdateMyProfileCommand.class,
                    userId, fullName, avatarUrl, bio, department);
        }
    }
}

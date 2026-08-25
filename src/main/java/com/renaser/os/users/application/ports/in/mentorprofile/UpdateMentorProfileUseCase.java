package com.renaser.os.users.application.ports.in.mentorprofile;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.mentorprofile.MentorLevel;
import com.renaser.os.users.domain.model.mentorprofile.MentorOperationalStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Campos null = "no cambiar". level/operationalStatus requieren actor.canManageRoles()
 * (ADMIN/ALCHEMIST); bio la puede tocar el propio mentor (ver MentorProfileService).
 */
public interface UpdateMentorProfileUseCase {

    void update(UpdateMentorProfileCommand command);

    record UpdateMentorProfileCommand(
            @NotNull UserId mentorUserId,
            MentorLevel newLevel,
            MentorOperationalStatus newOperationalStatus,
            String newBio,
            @NotNull UserId actorId) {

        public UpdateMentorProfileCommand {
            SelfValidating.validateConstructorArgs(UpdateMentorProfileCommand.class,
                    mentorUserId, newLevel, newOperationalStatus, newBio, actorId);
        }
    }
}

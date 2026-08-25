package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.mentorprofile.UpdateMentorProfileUseCase;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class MentorProfileService implements UpdateMentorProfileUseCase {

    private final LoadMentorProfilePort loadMentorProfilePort;
    private final SaveMentorProfilePort saveMentorProfilePort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final Clock clock;

    public MentorProfileService(LoadMentorProfilePort loadMentorProfilePort,
                                 SaveMentorProfilePort saveMentorProfilePort,
                                 RequireActiveUserGuard requireActiveUserGuard, Clock clock) {
        this.loadMentorProfilePort = loadMentorProfilePort;
        this.saveMentorProfilePort = saveMentorProfilePort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void update(UpdateMentorProfileCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        MentorProfile profile = requireProfile(command.mentorUserId());

        if (command.newLevel() != null || command.newOperationalStatus() != null) {
            requireRoleManager(actor);
        }
        if (command.newLevel() != null) {
            profile.promoteTo(command.newLevel(), clock);
        }
        if (command.newOperationalStatus() != null) {
            profile.changeOperationalStatus(command.newOperationalStatus(), clock);
        }
        if (command.newBio() != null) {
            requireSelfOrRoleManager(actor, command.mentorUserId());
            profile.updateBio(command.newBio(), clock);
        }
        saveMentorProfilePort.save(profile);
    }

    private void requireRoleManager(User actor) {
        if (!actor.canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST cambian nivel o estado operativo de un mentor");
        }
    }

    private void requireSelfOrRoleManager(User actor, UserId mentorUserId) {
        if (Objects.equals(actor.id(), mentorUserId) || actor.canManageRoles()) {
            return;
        }
        throw new NotAuthorizedException("Solo el propio mentor o ADMIN/ALCHEMIST editan esta bio");
    }

    private MentorProfile requireProfile(UserId mentorUserId) {
        return loadMentorProfilePort.byUserId(mentorUserId)
                .orElseThrow(() -> new NoSuchElementException("Perfil de mentor no encontrado: " + mentorUserId));
    }
}

package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.mentorprofile.SetMentorOperationalStatusUseCase;
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
public class MentorProfileService implements UpdateMentorProfileUseCase, SetMentorOperationalStatusUseCase {

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

    /**
     * El semaforo operativo, y solo el semaforo. El nivel N0-N3 no se toca por aca: para eso
     * esta {@link #update(UpdateMentorProfileCommand)}, que sigue exigiendo ADMIN/ALCHEMIST.
     */
    @Override
    @Transactional
    public void setOperationalStatus(SetMentorOperationalStatusCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        requirePuedeMoverElSemaforo(actor);
        MentorProfile profile = requireProfile(command.mentorUserId());
        profile.changeOperationalStatus(command.newStatus(), clock);
        saveMentorProfilePort.save(profile);
    }

    /**
     * Se enumeran los roles a mano en vez de preguntar {@code actor.role().can(...)} a proposito:
     * {@code UserRole.can} todavia falla-abierto para MENTOR, ADMIN y ALCHEMIST (deuda A-1), asi
     * que preguntarle aca dejaria pasar tambien a un MENTOR. El guard del servicio es la segunda
     * linea de defensa (CLAUDE.MD §5.3.4) y tiene que ser explicito.
     */
    private void requirePuedeMoverElSemaforo(User actor) {
        if (actor.role() != UserRole.MENTOR_LEAD && !actor.canManageRoles()) {
            throw new NotAuthorizedException(
                    "Solo MENTOR_LEAD/ADMIN/ALCHEMIST cambian el estado operativo de un mentor");
        }
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

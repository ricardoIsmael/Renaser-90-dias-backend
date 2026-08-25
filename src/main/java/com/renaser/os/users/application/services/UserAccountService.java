package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;

/**
 * Casos de uso de User que no son alta/aprobacion (eso vive en AccountRequestService).
 * Tambien implementa UserSummaryFinder (users/api): es la unica forma en que otro
 * modulo puede consultar un usuario, y nunca ve el User completo.
 */
@Service
public class UserAccountService implements InviteAndCreateUserUseCase, GetMyProfileUseCase,
        UpdateMyProfileUseCase, UpdateUserRoleUseCase, UserSummaryFinder {

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final LoadMentorProfilePort loadMentorProfilePort;
    private final SaveMentorProfilePort saveMentorProfilePort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public UserAccountService(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                               LoadMentorProfilePort loadMentorProfilePort,
                               SaveMentorProfilePort saveMentorProfilePort,
                               RequireActiveUserGuard requireActiveUserGuard, ApplicationEventPublisher events,
                               Clock clock) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.loadMentorProfilePort = loadMentorProfilePort;
        this.saveMentorProfilePort = saveMentorProfilePort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserId invite(InviteUserCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        User invited = User.invite(UserId.of(command.supabaseUserId()), new Email(command.email()),
                command.fullName(), command.role(), actor);
        User saved = saveUserPort.save(invited);
        ensureMentorProfileIfNeeded(saved);
        events.publishEvent(new UsuarioRegistradoEvent(saved.id(), clock.now()));
        return saved.id();
    }

    @Override
    public User getMyProfile(UserId userId) {
        return requireActiveUserGuard.of(userId);
    }

    @Override
    @Transactional
    public void updateMyProfile(UpdateMyProfileCommand command) {
        User user = requireActiveUserGuard.of(command.userId());
        if (command.fullName() != null) {
            user.rename(command.fullName());
        }
        if (command.avatarUrl() != null) {
            user.changeAvatar(command.avatarUrl());
        }
        if (command.bio() != null) {
            user.updateBio(command.bio());
        }
        if (command.department() != null) {
            user.updateDepartment(command.department());
        }
        saveUserPort.save(user);
    }

    @Override
    @Transactional
    public void updateRole(UpdateUserRoleCommand command) {
        User target = requireUser(command.targetUserId());
        User actor = requireActiveUserGuard.of(command.actorId());
        target.changeRole(command.newRole(), actor);
        User saved = saveUserPort.save(target);
        ensureMentorProfileIfNeeded(saved);
    }

    /** Camino simple de §4.3: MENTOR nuevo (por invitacion o por cambio de rol) recibe un perfil vacio. */
    private void ensureMentorProfileIfNeeded(User user) {
        if (user.role() != UserRole.MENTOR) {
            return;
        }
        boolean alreadyHasProfile = loadMentorProfilePort.byUserId(user.id()).isPresent();
        if (!alreadyHasProfile) {
            saveMentorProfilePort.save(MentorProfile.create(user.id(), clock));
        }
    }

    @Override
    public Optional<UserSummary> findById(UserId id) {
        return loadUserPort.byId(id).map(UserAccountService::aResumen);
    }

    @Override
    public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loadUserPort.byIds(ids).stream()
                .collect(Collectors.toMap(User::id, UserAccountService::aResumen));
    }

    private static UserSummary aResumen(User user) {
        return new UserSummary(user.id(), user.fullName(), user.avatarUrl(), user.role(), user.status());
    }

    /** Sin chequeo de {@code hasAccess()}: usado solo para cargar el OBJETIVO de un cambio de rol
     * (no el actor) — un usuario suspendido puede seguir siendo el destino de una operacion. */
    private User requireUser(UserId id) {
        return loadUserPort.byId(id).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
    }
}

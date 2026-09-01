package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.in.user.GetMyFullProfileUseCase;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateMyProfileUseCase;
import com.renaser.os.users.application.ports.in.user.UpdateUserRoleUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.mentorprofile.SaveMentorProfilePort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.user.Credencial;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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
        GetMyFullProfileUseCase, UpdateMyProfileUseCase, UpdateUserRoleUseCase, UserSummaryFinder {

    /** 20 bytes de {@link SecureRandom}, Base64 URL-safe sin padding: bien por encima del
     * minimo de 12 caracteres de docs/MODULO_AUTH.md §7.2, sin caracteres que compliquen
     * copiar/pegar el email (a diferencia de Base64 estandar, no lleva '+'/'/'). */
    private static final int TEMP_PASSWORD_BYTES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final LoadMentorProfilePort loadMentorProfilePort;
    private final SaveMentorProfilePort saveMentorProfilePort;
    private final LoadParticipacionProgramaPort loadParticipacionProgramaPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final SaveCredencialPort saveCredencialPort;
    private final EnviarEmailPort enviarEmailPort;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public UserAccountService(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                               LoadMentorProfilePort loadMentorProfilePort,
                               SaveMentorProfilePort saveMentorProfilePort,
                               LoadParticipacionProgramaPort loadParticipacionProgramaPort,
                               RequireActiveUserGuard requireActiveUserGuard, SaveCredencialPort saveCredencialPort,
                               EnviarEmailPort enviarEmailPort, PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.loadMentorProfilePort = loadMentorProfilePort;
        this.saveMentorProfilePort = saveMentorProfilePort;
        this.loadParticipacionProgramaPort = loadParticipacionProgramaPort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.saveCredencialPort = saveCredencialPort;
        this.enviarEmailPort = enviarEmailPort;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public UserId invite(InviteUserCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        User invited = User.invite(UserId.of(command.usuarioId()), new Email(command.email()),
                command.fullName(), command.role(), actor);
        User saved = saveUserPort.save(invited);
        ensureMentorProfileIfNeeded(saved);
        events.publishEvent(new UsuarioRegistradoEvent(saved.id(), clock.now()));
        return saved.id();
    }

    /** Panel admin de staff (gap #6) — ver javadoc de {@link InviteAndCreateUserUseCase#inviteStaff}. */
    @Override
    @Transactional
    public UserId inviteStaff(InviteStaffCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        // La identidad entra por el puerto IdGenerator, no se sortea aca (CLAUDE.MD 5.4.7, D-59).
        UserId newId = UserId.of(idGenerator.newId());
        User invited = User.invite(newId, new Email(command.email()), command.fullName(), command.role(), actor);
        User saved = saveUserPort.save(invited);
        ensureMentorProfileIfNeeded(saved);
        grantTemporaryPasswordAndNotify(saved);
        events.publishEvent(new UsuarioRegistradoEvent(saved.id(), clock.now()));
        return saved.id();
    }

    /**
     * D-49 (docs/MODULO_AUTH.md): ya no hay un proveedor externo (Supabase) que le pida
     * al invitado elegir su propia contrasena — asi que sin esto, un staff invitado desde
     * este panel jamas podria loguear. Genera una contrasena de un solo uso, la hashea
     * (nunca se persiste en claro) y la comunica por {@link EnviarEmailPort} — el invitado
     * debe cambiarla en su primer login (fuera de alcance de este caso de uso: es la
     * misma pantalla de "cambiar contrasena" que ya usa cualquier usuario autenticado).
     */
    private void grantTemporaryPasswordAndNotify(User user) {
        String temporaryPassword = generateTemporaryPassword();
        saveCredencialPort.guardar(user.id(), new Credencial(passwordEncoder.encode(temporaryPassword), clock.now()));
        enviarEmailPort.enviarInvitacionStaff(user.email().value(), temporaryPassword);
    }

    private static String generateTemporaryPassword() {
        byte[] bytes = new byte[TEMP_PASSWORD_BYTES];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public User getMyProfile(UserId userId) {
        return requireActiveUserGuard.of(userId);
    }

    /** Hueco #1 (docs/PLAN_INTEGRACION_FRONTEND.md): enriquece el perfil propio con el
     * resumen de `ParticipacionPrograma` cuando el actor tiene fila en `participantes_programa` —
     * ver javadoc de {@link GetMyFullProfileUseCase}. */
    @Override
    public MyProfile getMyFullProfile(UserId userId) {
        User user = requireActiveUserGuard.of(userId);
        TraineeProfileSummary resumen = loadParticipacionProgramaPort.byParticipanteId(userId)
                .map(UserAccountService::aResumenTrainee)
                .orElse(null);
        return new MyProfile(user, resumen);
    }

    private static TraineeProfileSummary aResumenTrainee(ParticipacionPrograma p) {
        return new TraineeProfileSummary(p.nombreRetoPersonal(), p.fechaInicio(),
                p.tipoMeta() == null ? null : p.tipoMeta().name(), p.programaCompletado(),
                p.programaCompletadoEn(), p.diaPostPrograma());
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

    /**
     * El avatar viaja como URL PERMANENTE (D-55): `chat`, `community`, `support` y `rag` la
     * reciben lista para mostrar y no necesitan enterarse de que existe un bucket. Es tambien
     * lo que deja que el cliente la cachee — una prefirmada cambiaria en cada respuesta y
     * obligaria a redescargar todos los avatares del muro cada vez (E-57).
     */
    private static UserSummary aResumen(User user) {
        return new UserSummary(user.id(), user.fullName(), user.avatarUrl(), user.role(), user.status());
    }

    /** Sin chequeo de {@code hasAccess()}: usado solo para cargar el OBJETIVO de un cambio de rol
     * (no el actor) — un usuario suspendido puede seguir siendo el destino de una operacion. */
    private User requireUser(UserId id) {
        return loadUserPort.byId(id).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
    }
}

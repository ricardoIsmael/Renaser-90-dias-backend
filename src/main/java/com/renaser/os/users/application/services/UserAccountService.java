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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

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
    private final TransactionTemplate transactionTemplate;

    public UserAccountService(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                               LoadMentorProfilePort loadMentorProfilePort,
                               SaveMentorProfilePort saveMentorProfilePort,
                               LoadParticipacionProgramaPort loadParticipacionProgramaPort,
                               RequireActiveUserGuard requireActiveUserGuard, SaveCredencialPort saveCredencialPort,
                               EnviarEmailPort enviarEmailPort, PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator,
                               TransactionTemplate transactionTemplate) {
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
        this.transactionTemplate = transactionTemplate;
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
    public UserId inviteStaff(InviteStaffCommand command) {
        StaffInvitado invitado = transactionTemplate.execute(status -> crearStaffConCredencial(command));
        // C-11: el envio ocurre con la transaccion YA CERRADA y la conexion devuelta al pool.
        enviarInvitacionSinPerderElAlta(invitado.user(), invitado.temporaryPassword());
        return invitado.user().id();
    }

    /**
     * Lo unico que necesita atomicidad del alta de staff: usuario, perfil de mentor si
     * corresponde, credencial temporal y evento. Si algo de esto falla, Postgres deshace
     * todo y no queda un staff a medio crear (por ejemplo, un usuario sin credencial, que
     * jamas podria loguear). Devuelve la contrasena en claro para que
     * {@link #inviteStaff} la mande por correo despues del commit: nunca se persiste asi.
     *
     * <p>D-49 (docs/MODULO_AUTH.md): ya no hay un proveedor externo (Supabase) que le pida
     * al invitado elegir su propia contrasena, asi que sin esta credencial un staff
     * invitado desde el panel jamas podria entrar. Debe cambiarla en su primer login.
     */
    private StaffInvitado crearStaffConCredencial(InviteStaffCommand command) {
        User actor = requireActiveUserGuard.of(command.actorId());
        // La identidad entra por el puerto IdGenerator, no se sortea aca (CLAUDE.MD 5.4.7, D-59).
        UserId newId = UserId.of(idGenerator.newId());
        User invited = User.invite(newId, new Email(command.email()), command.fullName(), command.role(), actor);
        User saved = saveUserPort.save(invited);
        ensureMentorProfileIfNeeded(saved);
        String temporaryPassword = generateTemporaryPassword();
        saveCredencialPort.guardar(saved.id(), new Credencial(passwordEncoder.encode(temporaryPassword), clock.now()));
        events.publishEvent(new UsuarioRegistradoEvent(saved.id(), clock.now()));
        return new StaffInvitado(saved, temporaryPassword);
    }

    /**
     * <b>C-11 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html).</b> Un SMTP
     * que no responde retenia una conexion de Hikari los 15s de los 3 reintentos del cliente
     * de mail; un admin cargando una cohorte de staff podia agotar el pool para TODA la API,
     * no solo para las invitaciones.
     *
     * <p>Por que un {@link TransactionTemplate} y no diferir el envio con
     * {@code TransactionSynchronization.afterCommit()}, que es el patron de
     * {@code MensajeService.publicarDespuesDelCommit} (chat): <b>ese patron no alcanza aca</b>.
     * Spring corre los callbacks de {@code afterCommit} y {@code afterCompletion} ANTES de
     * {@code cleanupAfterCompletion}, que es donde se desliga el EntityManager y la conexion
     * vuelve al pool — o sea que el envio seguiria corriendo con la conexion tomada, que es
     * exactamente lo que C-11 pide evitar. En {@code chat} el patron sirve porque ahi el
     * efecto diferido es publicar en memoria (instantaneo); un servidor SMTP no lo es.
     * Lo verifica {@code UserAccountServiceInvitarStaffTransaccionIT}, midiendo
     * {@code isActualTransactionActive()} desde adentro del envio contra Postgres real.
     */
    private record StaffInvitado(User user, String temporaryPassword) { }

    /**
     * Decision explicita sobre que pasa si el correo nunca llega (C-11): el alta YA COMMITEO
     * -- usuario y credencial existen -- asi que un fallo de SMTP aca nunca se propaga (el
     * caller ya recibio, o va a recibir, una respuesta exitosa que no puede desdecirse). No
     * hay reintento automatico de ENVIO porque no hace falta uno nuevo: quien no reciba este
     * correo ya tiene una {@code Credencial} real guardada, asi que puede recuperar acceso
     * por el flujo normal de "olvide mi contrasena" (docs/MODULO_AUTH.md), igual que
     * cualquier otro usuario -- no queda varado. Se loguea en ERROR para que quede visible
     * en observabilidad (CLAUDE.MD §5.4.9), sin exponer el email (PII) ni la contrasena.
     */
    private void enviarInvitacionSinPerderElAlta(User user, String temporaryPassword) {
        try {
            enviarEmailPort.enviarInvitacionStaff(user.email().value(), temporaryPassword);
        } catch (RuntimeException e) {
            log.error("[users.UserAccountService] fallo el envio del correo de invitacion de staff para el "
                    + "usuario {}; el alta ya quedo confirmada (usuario + credencial) y no se pierde -- quien no "
                    + "reciba el correo puede recuperar acceso con 'olvide mi contrasena'", user.id(), e);
        }
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

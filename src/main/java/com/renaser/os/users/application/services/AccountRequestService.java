package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.TokenVerificacionEmailInvalidoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.CheckAccountRequestStatusUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.DeleteAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.DeleteAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SaveAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.application.ports.out.accountrequest.SupabaseAdminAuthPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import com.renaser.os.users.domain.model.user.Credencial;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Casos de uso de AccountRequest (CLAUDE.MD §4.3). Una clase por agregado agrupando
 * sus casos de uso relacionados — variante de Hombergs/estructura del profesor
 * (application/services), reconciliada con la regla de este repo de puertos chicos
 * por intencion (§5.4.8): cada metodo publico sigue siendo UN caso de uso, la clase
 * solo evita un archivo de una linea por cada uno.
 */
@Service
public class AccountRequestService implements SubmitAccountRequestUseCase, ApproveAccountRequestUseCase,
        RejectAccountRequestUseCase, ListAccountRequestsUseCase, DeleteAccountRequestUseCase,
        CheckAccountRequestStatusUseCase {

    private static final int RATE_LIMIT_PER_HOUR = 60;

    // Ya no hay token ni correo de activacion (2026-08-27): la contrasena se elige en el alta,
    // asi que aprobar no necesita mandar nada. Quien olvide su clave usa el flujo normal de
    // "olvide mi contrasena" (ResetContrasenaService), que es exactamente para eso.

    private final LoadAccountRequestPort loadAccountRequestPort;
    private final SaveAccountRequestPort saveAccountRequestPort;
    private final DeleteAccountRequestPort deleteAccountRequestPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final DeleteUserPort deleteUserPort;
    private final SaveCredencialPort saveCredencialPort;
    private final PasswordEncoder passwordEncoder;
    private final SupabaseAdminAuthPort supabaseAdminAuthPort;
    private final SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final RequireAdminGuard requireAdminGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountRequestService(LoadAccountRequestPort loadAccountRequestPort,
                                  SaveAccountRequestPort saveAccountRequestPort,
                                  DeleteAccountRequestPort deleteAccountRequestPort,
                                  LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                  DeleteUserPort deleteUserPort, SaveCredencialPort saveCredencialPort,
                                  PasswordEncoder passwordEncoder,
                                  SupabaseAdminAuthPort supabaseAdminAuthPort,
                                  SaveParticipacionProgramaPort saveParticipacionProgramaPort,
                                  TokenVerificacionEmailPort tokenVerificacionEmailPort,
                                  RequireActiveUserGuard requireActiveUserGuard, RequireAdminGuard requireAdminGuard,
                                  ApplicationEventPublisher events, Clock clock) {
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.saveAccountRequestPort = saveAccountRequestPort;
        this.deleteAccountRequestPort = deleteAccountRequestPort;
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.deleteUserPort = deleteUserPort;
        this.saveCredencialPort = saveCredencialPort;
        this.passwordEncoder = passwordEncoder;
        this.supabaseAdminAuthPort = supabaseAdminAuthPort;
        this.saveParticipacionProgramaPort = saveParticipacionProgramaPort;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.requireAdminGuard = requireAdminGuard;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Genera el UUID del solicitante acá adentro (2026-08-27, D-49): hasta ahora este id lo
     * creaba Supabase Auth del lado del cliente y viajaba en el request — desde que Renaser OS
     * emite y valida su propia identidad de punta a punta, no hay ninguna razón para depender
     * de un tercero para algo tan simple como un UUID nuevo. Mismo patrón que
     * {@code AccountRequestId.newId()}/{@code CelulaId.newId()} en el resto del código.
     *
     * <p>Exige y consume {@code verificationToken} (2026-08-27): el reemplazo propio del email
     * de un solo uso que antes emitia Supabase Auth. Se valida ANTES de tocar nada mas — si el
     * token no existe, ya vencio, o certifica un email distinto al del comando (alguien
     * verifico un correo e intento usarlo para dar de alta otro), la solicitud ni se arma.
     */
    @Override
    @Transactional
    public AccountRequestId submit(SubmitAccountRequestCommand command) {
        rejectIfRateLimitExceeded(command.requestIp());
        requireEmailVerificado(command.email(), command.verificationToken());

        Email email = new Email(command.email());
        User usuario = User.registrarPendienteAprobacion(UserId.of(UUID.randomUUID()), email,
                command.fullName());
        saveUserPort.save(usuario);
        guardarCredencialSiEligioContrasena(usuario, command.contrasena());

        AccountRequest request = AccountRequest.submit(usuario.id(), email,
                command.fullName(), command.phone(), command.city(), command.requestIp(), clock);
        return saveAccountRequestPort.save(request).id();
    }

    /**
     * El alta por formulario trae contrasena; la de proveedor social no (entra por Google/Apple
     * y {@code usuarios.hash_contrasena} queda null, que es justo para lo que esa columna era
     * nullable). La credencial se escribe aparte del usuario a proposito: {@code
     * SaveCredencialPort} solo toca las dos columnas de contrasena, asi el hash nunca pasa por
     * {@code UserJpaEntity} y no puede salir por una respuesta HTTP ni por un log
     * (docs/MODULO_AUTH.md §2.2).
     */
    private void guardarCredencialSiEligioContrasena(User usuario, String contrasenaEnClaro) {
        if (contrasenaEnClaro == null) {
            return;
        }
        saveCredencialPort.guardar(usuario.id(),
                new Credencial(passwordEncoder.encode(contrasenaEnClaro), clock.now()));
    }

    private void requireEmailVerificado(String email, String verificationToken) {
        String emailVerificado = tokenVerificacionEmailPort.consumir(verificationToken).orElse(null);
        if (emailVerificado == null || !emailVerificado.equalsIgnoreCase(email)) {
            throw new TokenVerificacionEmailInvalidoException();
        }
    }

    @Override
    @Transactional
    public void approve(ApproveAccountRequestCommand command) {
        AccountRequest request = requireRequest(command.accountRequestId());
        User actor = requireActiveUserGuard.of(command.actorId());

        // El usuario YA existe desde el alta, en estado INACTIVE y con su contrasena elegida
        // (ver SubmitAccountRequestUseCase): aprobar solo le da acceso. Antes se creaba aca,
        // lo que obligaba a un segundo correo de activacion para que fijara su clave — dos
        // envios en el camino critico y nadie sabia por que no llegaba el segundo.
        User usuario = loadUserPort.byId(request.supabaseUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "La solicitud no tiene usuario asociado: " + request.supabaseUserId()));
        usuario.aprobar();
        saveUserPort.save(usuario);

        // Invariante de participantes_programa (baseline V1, comentario de la tabla): el
        // programa de 90 dias es obligatorio para TRAINEE y la fila se crea al aprobar la
        // cuenta, en la MISMA transaccion — el alta fuerza el rol TRAINEE, asi que todo alta
        // por este camino la necesita. Porte de
        // onboarding/service.ts::createTraineePendingActivation (repo viejo): el reloj del
        // programa arranca pausado, lo activa despues el primer login + Ficha + Terminos.
        saveParticipacionProgramaPort.save(ParticipacionPrograma.inscribirTraineeAprobado(usuario.id(), clock));
        request.approve(actor, usuario.id(), clock);
        saveAccountRequestPort.save(request);

        events.publishEvent(new UsuarioRegistradoEvent(usuario.id(), clock.now()));
    }

    @Override
    @Transactional
    public void reject(RejectAccountRequestCommand command) {
        AccountRequest request = requireRequest(command.accountRequestId());
        User actor = requireActiveUserGuard.of(command.actorId());

        request.reject(actor, command.reason(), clock);
        saveAccountRequestPort.save(request);

        // Borrar el usuario es OBLIGATORIO desde que el alta lo crea (2026-08-27): sin esto,
        // una solicitud rechazada dejaria la fila en `usuarios` ocupando el correo para
        // siempre (el indice UNIQUE de usuarios.email), y ademas conservando una credencial
        // de alguien que no es usuario. Es el mismo squatting de correos que el repo viejo
        // resolvia con `deleteUser` en Supabase (PROPOSAL_ACCOUNT_REQUESTS.md punto 3), ahora
        // sobre nuestra propia tabla. Idempotente: si no existe, no falla (E-44).
        deleteUserPort.deleteById(request.supabaseUserId());
        deleteSupabaseUserAfterCommit(request.supabaseUserId());
    }

    private void rejectIfRateLimitExceeded(String requestIp) {
        if (requestIp == null) {
            return;
        }
        long recent = loadAccountRequestPort.countSubmittedFromIpSince(requestIp,
                clock.now().minus(Duration.ofHours(1)));
        if (recent >= RATE_LIMIT_PER_HOUR) {
            throw new RateLimitExceededException("Limite de solicitudes por hora excedido para IP " + requestIp);
        }
    }

    private AccountRequest requireRequest(AccountRequestId id) {
        return loadAccountRequestPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitud no encontrada: " + id));
    }

    // Se elimino compensateSupabaseUserOnRollback (2026-08-27): compensaba la creacion de un
    // usuario en Supabase Auth durante approve(), y approve() ya no crea a nadie. La creacion
    // vive ahora en submit(), dentro de UNA sola transaccion contra NUESTRO Postgres — si algo
    // falla, el rollback deshace usuario, credencial y solicitud junto, sin compensacion manual
    // (CLAUDE.MD §9.1: el rollback gratis es justamente lo que da el monolito).

    /** §5.3.6: rechazar SIEMPRE libera el email, solo una vez que el rechazo quedo durable. */
    private void deleteSupabaseUserAfterCommit(UserId supabaseUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            supabaseAdminAuthPort.deleteUser(supabaseUserId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                supabaseAdminAuthPort.deleteUser(supabaseUserId);
            }
        });
    }

    /** Panel admin de solicitudes de cuenta (gap #9). Listado, sin un recurso previo por
     * id que proteger: el gate de admin va primero. */
    @Override
    public PaginaAccountRequests listar(ListAccountRequestsCommand command) {
        requireAdminGuard.requireAdminActivo(command.actorId());
        var contenido = loadAccountRequestPort.pagina(command.statusFilter(), command.page(), command.size());
        long total = loadAccountRequestPort.contar(command.statusFilter());
        return new PaginaAccountRequests(contenido, total, command.page(), command.size());
    }

    /** El recurso se carga PRIMERO (404 si no existe), el gate de admin va DESPUES
     * (docs/BITACORA_ERRORES.md E-42), fail-closed via {@link RequireAdminGuard}. */
    @Override
    @Transactional
    public void eliminar(DeleteAccountRequestUseCase.DeleteAccountRequestCommand command) {
        requireRequest(command.requestId());
        requireAdminGuard.requireAdminActivo(command.actorId());
        deleteAccountRequestPort.deleteById(command.requestId());
    }

    /** PUBLIC_ENDPOINT (gap #9) — ver javadoc de {@link CheckAccountRequestStatusUseCase}. */
    @Override
    public AccountRequestStatusView consultar(AccountRequestId requestId) {
        AccountRequest request = requireRequest(requestId);
        return new AccountRequestStatusView(request.status(), request.rejectionReason());
    }
}

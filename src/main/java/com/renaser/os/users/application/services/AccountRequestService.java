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
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.application.ports.out.accountrequest.SupabaseAdminAuthPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
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

    /**
     * Vigencia del link de activacion (2026-08-27) — mas larga que la del reset de contrasena
     * (30 min, ResetContrasenaService.VIGENCIA_TOKEN): esa es para alguien que ya esta mirando
     * la pantalla de "olvide mi contrasena" en el momento; esta es para alguien que se entera
     * de la aprobacion por correo, posiblemente horas despues. Valor no confirmado por
     * producto, asumido por analogia — documentado para que se ajuste si hace falta.
     */
    static final Duration VIGENCIA_TOKEN_ACTIVACION = Duration.ofHours(24);

    private final LoadAccountRequestPort loadAccountRequestPort;
    private final SaveAccountRequestPort saveAccountRequestPort;
    private final DeleteAccountRequestPort deleteAccountRequestPort;
    private final SaveUserPort saveUserPort;
    private final SupabaseAdminAuthPort supabaseAdminAuthPort;
    private final SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    private final TokenResetContrasenaPort tokenResetContrasenaPort;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;
    private final EnviarEmailPort enviarEmailPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final RequireAdminGuard requireAdminGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountRequestService(LoadAccountRequestPort loadAccountRequestPort,
                                  SaveAccountRequestPort saveAccountRequestPort,
                                  DeleteAccountRequestPort deleteAccountRequestPort, SaveUserPort saveUserPort,
                                  SupabaseAdminAuthPort supabaseAdminAuthPort,
                                  SaveParticipacionProgramaPort saveParticipacionProgramaPort,
                                  TokenResetContrasenaPort tokenResetContrasenaPort,
                                  TokenVerificacionEmailPort tokenVerificacionEmailPort, EnviarEmailPort enviarEmailPort,
                                  RequireActiveUserGuard requireActiveUserGuard, RequireAdminGuard requireAdminGuard,
                                  ApplicationEventPublisher events, Clock clock) {
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.saveAccountRequestPort = saveAccountRequestPort;
        this.deleteAccountRequestPort = deleteAccountRequestPort;
        this.saveUserPort = saveUserPort;
        this.supabaseAdminAuthPort = supabaseAdminAuthPort;
        this.saveParticipacionProgramaPort = saveParticipacionProgramaPort;
        this.tokenResetContrasenaPort = tokenResetContrasenaPort;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
        this.enviarEmailPort = enviarEmailPort;
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

        UserId nuevoUserId = UserId.of(UUID.randomUUID());

        AccountRequest request = AccountRequest.submit(nuevoUserId, new Email(command.email()),
                command.fullName(), command.phone(), command.city(), command.requestIp(), clock);
        return saveAccountRequestPort.save(request).id();
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

        User newUser = User.registerTrainee(request.supabaseUserId(), request.email(), request.fullName());
        compensateSupabaseUserOnRollback(newUser.id());

        saveUserPort.save(newUser);
        // Invariante de participantes_programa (baseline V1, comentario de la tabla): el
        // programa de 90 dias es obligatorio para TRAINEE y la fila se crea al aprobar la
        // cuenta, en la MISMA transaccion — registerTrainee() fuerza el rol TRAINEE, asi
        // que todo alta por este camino la necesita. Porte de
        // onboarding/service.ts::createTraineePendingActivation (repo viejo): el reloj del
        // programa arranca pausado, lo activa despues el primer login + Ficha + Terminos.
        saveParticipacionProgramaPort.save(ParticipacionPrograma.inscribirTraineeAprobado(newUser.id(), clock));
        request.approve(actor, newUser.id(), clock);
        saveAccountRequestPort.save(request);

        // 2026-08-27: el aprendiz recien creado no tiene Credencial (el alta no captura
        // contrasena) — reusa el MISMO mecanismo que ResetContrasenaService (token opaco en
        // Redis, confirmar via ConfirmarResetContrasenaUseCase), asi que "activar cuenta" y
        // "recuperar contrasena" comparten toda la infraestructura, solo cambia el correo.
        String tokenActivacion = tokenResetContrasenaPort.generar(newUser.id(), VIGENCIA_TOKEN_ACTIVACION);
        enviarEmailPort.enviarActivacionCuenta(newUser.email().value(), tokenActivacion);

        events.publishEvent(new UsuarioRegistradoEvent(newUser.id(), clock.now()));
    }

    @Override
    @Transactional
    public void reject(RejectAccountRequestCommand command) {
        AccountRequest request = requireRequest(command.accountRequestId());
        User actor = requireActiveUserGuard.of(command.actorId());

        request.reject(actor, command.reason(), clock);
        saveAccountRequestPort.save(request);

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

    /**
     * §5.3.3/§9.1: si la transaccion no llega a commit, el usuario ya creado en
     * Supabase Auth queda liberado (afterCompletion, no afterCommit: se dispara
     * exactamente cuando el rollback ya paso).
     */
    private void compensateSupabaseUserOnRollback(UserId supabaseUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    supabaseAdminAuthPort.deleteUser(supabaseUserId);
                }
            }
        });
    }

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

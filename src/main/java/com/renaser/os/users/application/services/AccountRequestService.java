package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.accountrequest.SaveAccountRequestPort;
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

/**
 * Casos de uso de AccountRequest (CLAUDE.MD §4.3). Una clase por agregado agrupando
 * sus casos de uso relacionados — variante de Hombergs/estructura del profesor
 * (application/services), reconciliada con la regla de este repo de puertos chicos
 * por intencion (§5.4.8): cada metodo publico sigue siendo UN caso de uso, la clase
 * solo evita un archivo de una linea por cada uno.
 */
@Service
public class AccountRequestService implements SubmitAccountRequestUseCase, ApproveAccountRequestUseCase,
        RejectAccountRequestUseCase {

    private static final int RATE_LIMIT_PER_HOUR = 60;

    private final LoadAccountRequestPort loadAccountRequestPort;
    private final SaveAccountRequestPort saveAccountRequestPort;
    private final SaveUserPort saveUserPort;
    private final SupabaseAdminAuthPort supabaseAdminAuthPort;
    private final SaveParticipacionProgramaPort saveParticipacionProgramaPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AccountRequestService(LoadAccountRequestPort loadAccountRequestPort,
                                  SaveAccountRequestPort saveAccountRequestPort, SaveUserPort saveUserPort,
                                  SupabaseAdminAuthPort supabaseAdminAuthPort,
                                  SaveParticipacionProgramaPort saveParticipacionProgramaPort,
                                  RequireActiveUserGuard requireActiveUserGuard, ApplicationEventPublisher events,
                                  Clock clock) {
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.saveAccountRequestPort = saveAccountRequestPort;
        this.saveUserPort = saveUserPort;
        this.supabaseAdminAuthPort = supabaseAdminAuthPort;
        this.saveParticipacionProgramaPort = saveParticipacionProgramaPort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AccountRequestId submit(SubmitAccountRequestCommand command) {
        rejectIfRateLimitExceeded(command.requestIp());

        UserId supabaseUserId = UserId.of(command.supabaseUserId());
        compensateSupabaseUserOnRollback(supabaseUserId);

        AccountRequest request = AccountRequest.submit(supabaseUserId, new Email(command.email()),
                command.fullName(), command.phone(), command.city(), command.requestIp(), clock);
        return saveAccountRequestPort.save(request).id();
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
}

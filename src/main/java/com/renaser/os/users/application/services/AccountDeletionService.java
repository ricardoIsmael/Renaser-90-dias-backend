package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.user.CancelAccountDeletionUseCase;
import com.renaser.os.users.application.ports.in.user.GetAccountDeletionStatusUseCase;
import com.renaser.os.users.application.ports.in.user.PurgeExpiredAccountsUseCase;
import com.renaser.os.users.application.ports.in.user.RequestAccountDeletionUseCase;
import com.renaser.os.users.application.ports.out.user.DeleteUserPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import com.renaser.os.users.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Baja de cuenta autogestionada (gap #5, requisito Google Play/Apple) — portada 1:1 de
 * features/account-deletion (backend viejo, Next.js): soft-delete diferido con purga por
 * cron. Ver {@code EstadoBajaCuenta} para la logica pura del plazo de gracia y
 * {@code docs/BITACORA_ERRORES.md}/{@code docs/MODULO_USERS.md} para la decision de los
 * dias de gracia (14, confirmado — no un supuesto: coincide el comentario de
 * {@code usuarios.baja_solicitada_en} en el baseline SQL con {@code DIAS_DE_GRACIA} del
 * backend viejo).
 */
@Service
public class AccountDeletionService implements RequestAccountDeletionUseCase, CancelAccountDeletionUseCase,
        GetAccountDeletionStatusUseCase, PurgeExpiredAccountsUseCase {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** Google Play (2024) / Apple 5.1.1(v): confirmacion explicita para que ni un retry
     * automatico ni un request suelto borren una cuenta (backend viejo, PALABRA_DE_CONFIRMACION).
     * No sustituye a la reautenticacion, que hace el cliente contra su propia sesion antes
     * de llamar. */
    public static final String PALABRA_CONFIRMACION = "ELIMINAR";

    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final DeleteUserPort deleteUserPort;
    private final RequireActiveUserGuard requireActiveUserGuard;
    private final Clock clock;
    private final int diasDeGracia;

    public AccountDeletionService(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                   DeleteUserPort deleteUserPort, RequireActiveUserGuard requireActiveUserGuard,
                                   Clock clock,
                                   @Value("${renaser.users.account-deletion.grace-period-days:14}")
                                   int diasDeGracia) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.deleteUserPort = deleteUserPort;
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.clock = clock;
        this.diasDeGracia = diasDeGracia;
    }

    @Override
    @Transactional
    public EstadoBajaCuenta request(RequestAccountDeletionCommand command) {
        if (!PALABRA_CONFIRMACION.equals(command.confirmacion())) {
            throw new IllegalArgumentException(
                    "CONFIRMATION_REQUIRED: la confirmacion debe ser \"" + PALABRA_CONFIRMACION + "\"");
        }
        User user = requireActiveUserGuard.of(command.userId());
        // Idempotente: si ya habia una solicitud, User.solicitarBaja no la reinicia.
        user.solicitarBaja(clock);
        saveUserPort.save(user);
        return EstadoBajaCuenta.de(user.bajaSolicitadaEn(), clock.now(), diasDeGracia);
    }

    @Override
    @Transactional
    public EstadoBajaCuenta cancel(UserId userId) {
        User user = requireActiveUserGuard.of(userId);
        user.cancelarBaja();
        saveUserPort.save(user);
        return EstadoBajaCuenta.sinSolicitud(diasDeGracia);
    }

    @Override
    public EstadoBajaCuenta status(UserId userId) {
        User user = requireActiveUserGuard.of(userId);
        return EstadoBajaCuenta.de(user.bajaSolicitadaEn(), clock.now(), diasDeGracia);
    }

    /**
     * El cron diario (ver {@code PurgarCuentasBajaScheduler}). Cada cuenta se intenta por
     * separado — un fallo puntual no puede dejar sin purgar a las demas (mismo criterio que
     * el cron viejo, features/account-deletion/service.ts#purgarBajasVencidas).
     *
     * <p>A diferencia del backend viejo (Prisma + Supabase Auth: 26 tablas + Storage + Auth
     * borrados a mano), acá alcanza con {@link DeleteUserPort#deleteById} — las ~30 FK
     * contra `usuarios` en el baseline son ON DELETE CASCADE (o SET NULL en las de
     * auditoria) y desde D-49 nosotros somos dueños de credenciales/identidades, asi que un
     * solo DELETE limpia todo y libera el email (UNIQUE) para un nuevo registro.
     */
    @Override
    public ResultadoPurga purgeExpired() {
        var corte = clock.now().minus(diasDeGracia, ChronoUnit.DAYS);
        List<UserId> candidatas = loadUserPort.pendingDeletionUpTo(corte);
        int purgadas = 0;
        int fallidas = 0;
        for (UserId id : candidatas) {
            try {
                deleteUserPort.deleteById(id);
                purgadas++;
            } catch (RuntimeException e) {
                fallidas++;
                log.error("[users.AccountDeletionService] fallo purgando la cuenta {}", id, e);
            }
        }
        return new ResultadoPurga(purgadas, fallidas);
    }
}

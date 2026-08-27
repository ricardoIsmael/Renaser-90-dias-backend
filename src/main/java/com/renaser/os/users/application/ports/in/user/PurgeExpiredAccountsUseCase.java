package com.renaser.os.users.application.ports.in.user;

/**
 * El cron diario que purga (hard delete) las cuentas cuyo plazo de gracia vencio. No es un
 * detalle de infraestructura: es la mitad que hace real el cumplimiento GDPR/Google
 * Play/Apple - sin esta purga, {@link RequestAccountDeletionUseCase} solo marcaria una
 * fecha sin que nada pase nunca.
 */
public interface PurgeExpiredAccountsUseCase {

    ResultadoPurga purgeExpired();

    /**
     * Cada cuenta se purga en su propio intento: un fallo puntual (una fila con una FK
     * inesperada, un problema transitorio de conexion) no puede dejar sin purgar a las
     * demas - mismo criterio que el cron viejo (features/account-deletion/service.ts#purgarBajasVencidas).
     */
    record ResultadoPurga(int purgadas, int fallidas) {
    }
}

package com.renaser.os.users.application.ports.in.user;

/**
 * El barrido que borra para siempre las cuentas cerradas cuya gracia vencio (D-243: corre cada hora,
 * paginado, una cuenta a la vez). No es un detalle de infraestructura: es la mitad que hace real el
 * borrado que exigen Google Play y Apple — sin esta purga, cerrar la cuenta solo marcaria una fecha.
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

package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.shared.domain.UserId;

/**
 * Borrado DURO y definitivo de la fila `usuarios`. Separado de {@link SaveUserPort} porque
 * es una operacion distinta (no un upsert): la usa la purga de bajas de cuenta vencidas
 * (AccountDeletionService) y, a diferencia del backend viejo (Prisma + Supabase Auth), no
 * necesita barrer 26 tablas a mano ni borrar un usuario de Auth aparte - las ~30 FK contra
 * `usuarios` en el baseline son ON DELETE CASCADE (o SET NULL para las de auditoria), y
 * desde D-49 nosotros somos dueños de credenciales/identidades.
 *
 * <p><b>Esto NO es el contrato completo de borrado de cuenta</b>, y creer que si lo era es lo
 * que dejo vivas las solicitudes de alta de las cuentas purgadas hasta el 2026-09-21:
 * `solicitudes_cuenta` no cuelga de `usuarios` por ninguna FK borrable, y el bucket de S3 no
 * esta en el grafo de FK para empezar. Quien purga una cuenta tiene que resolver las dos cosas
 * explicitamente — ver {@code AccountDeletionService#purgeExpired}, que es el unico llamador.
 */
public interface DeleteUserPort {

    /** Idempotente: borrar un id que ya no existe no falla. */
    void deleteById(UserId id);
}

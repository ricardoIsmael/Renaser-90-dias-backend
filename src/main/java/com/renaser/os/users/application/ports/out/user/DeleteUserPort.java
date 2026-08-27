package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.shared.domain.UserId;

/**
 * Borrado DURO y definitivo de la fila `usuarios`. Separado de {@link SaveUserPort} porque
 * es una operacion distinta (no un upsert): la usa la purga de bajas de cuenta vencidas
 * (AccountDeletionService) y, a diferencia del backend viejo (Prisma + Supabase Auth), no
 * necesita barrer 26 tablas a mano ni borrar un usuario de Auth aparte - las ~30 FK contra
 * `usuarios` en el baseline son ON DELETE CASCADE (o SET NULL para las de auditoria), y
 * desde D-49 nosotros somos dueños de credenciales/identidades, asi que un solo DELETE
 * libera el email (UNIQUE) y limpia todo lo demas via Postgres.
 */
public interface DeleteUserPort {

    /** Idempotente: borrar un id que ya no existe no falla. */
    void deleteById(UserId id);
}

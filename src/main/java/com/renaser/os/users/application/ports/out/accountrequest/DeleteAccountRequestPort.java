package com.renaser.os.users.application.ports.out.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;

/** Panel admin de solicitudes de cuenta (gap #9): borrado, separado de `Save` (ISP, §5.4.8). */
public interface DeleteAccountRequestPort {

    /** {@code true} si existia y se borro; {@code false} si no existia (idempotente). */
    boolean deleteById(AccountRequestId id);

    /**
     * Borra la solicitud de alta de la que ese usuario es dueno, si la tiene.
     *
     * <p><b>Por que hace falta un borrado por usuario y no alcanza el de arriba.</b> El hard
     * delete de la cuenta ({@code DeleteUserPort#deleteById}) NO puede alcanzar esta fila por
     * cascade: {@code solicitudes_cuenta.usuario_id} —la unica columna que dice de quien es la
     * solicitud— no tiene FK contra {@code usuarios} (baseline V1, renombrada por V11), y las
     * dos que si la tienen ({@code revisada_por}, {@code usuario_creado_id}) son
     * {@code ON DELETE SET NULL}: cortan el vinculo y dejan la fila entera en pie. Sin esta
     * llamada, la purga deja en la base primaria el correo, el nombre completo, el UUID de la
     * cuenta ya borrada, la IP del registro y —cuando se dieron— telefono, ciudad y el sujeto
     * del proveedor social; y el correo nunca vuelve a quedar libre, porque
     * {@code existsByEmail} sigue contando esa fila.
     *
     * <p>Idempotente, igual que {@code DeleteUserPort#deleteById}: una cuenta sin solicitud
     * —el staff que entra por {@code POST /api/v1/users/invite} no tiene ninguna— no es un
     * error, no hay nada que borrar.
     *
     * <p><b>Necesita una transaccion en curso</b> (Spring Data no abre una sola para las
     * consultas derivadas): el llamador es dueno del limite. Ver
     * {@code AccountDeletionService#purgeExpired}.
     */
    void borrarPorUsuario(UserId usuarioId);
}

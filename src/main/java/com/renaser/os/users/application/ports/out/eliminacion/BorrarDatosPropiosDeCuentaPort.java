package com.renaser.os.users.application.ports.out.eliminacion;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.Email;

/**
 * Lo que {@code users} borra de una cuenta, en sus propias tablas, despues de que cada modulo borro
 * lo suyo ({@code users.api.BorradoDeDatosDeCuenta}, D-243): ajustes del dia (los de la persona),
 * participacion en el programa, perfil de mentor, identidades de Google/Apple, solicitudes de alta y
 * la fila de {@code usuarios}.
 *
 * <p>Las solicitudes de alta se borran tambien por CORREO y no solo por {@code usuario_id}:
 * {@code solicitudes_cuenta.email} es UNIQUE y la consulta {@code existsByEmail} del alta no
 * distingue una solicitud viva de la de una cuenta ya borrada. Una que quede con ese correo impide
 * volver a registrarse con el (requisito 3 del dueño).
 */
public interface BorrarDatosPropiosDeCuentaPort {

    /** Idempotente: una cuenta que ya no existe no falla. */
    void borrarTodoDe(UserId cuenta, Email email);
}

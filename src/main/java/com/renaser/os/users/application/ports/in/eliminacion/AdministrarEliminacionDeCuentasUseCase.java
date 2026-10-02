package com.renaser.os.users.application.ports.in.eliminacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Desde Administracion (decision 2 del dueño, D-243): eliminar una cuenta en el acto y para siempre,
 * o recuperar una que la persona cerro y todavia esta dentro de la gracia.
 */
public interface AdministrarEliminacionDeCuentasUseCase {

    /** 404 si no existe; 403 si el actor no puede; 400 si el correo escrito no es el de la cuenta. */
    void eliminar(UserId actor, UserId cuenta, String correoEscrito);

    /** 404 si no existe; 403 si el actor no es ADMIN/ALCHEMIST activo; 409 si no estaba cerrada. */
    void recuperar(UserId actor, UserId cuenta);
}

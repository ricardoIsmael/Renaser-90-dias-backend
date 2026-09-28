package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.users.domain.model.user.User;

/** Lo que la aplicacion necesita para ESCRIBIR usuarios. */
public interface SaveUserPort {

    /** Guarda el usuario tal como esta: si ya hay una fila con su id, la reemplaza (un upsert). */
    User save(User user);

    /**
     * Guarda una cuenta NUEVA y nunca pisa una existente: si ya hay una fila con ese id, falla en vez de
     * reemplazarla (en HTTP, un 409). Existe por E-365: invitar pasaba por {@link #save}, y con el id de
     * una cuenta que ya existia la reemplazaba entera, rol y estado incluidos.
     */
    User registrarNueva(User user);
}

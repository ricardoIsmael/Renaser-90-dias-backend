package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.users.domain.model.user.User;

/** Lo que la aplicacion necesita para ESCRIBIR usuarios. */
public interface SaveUserPort {

    User save(User user);
}

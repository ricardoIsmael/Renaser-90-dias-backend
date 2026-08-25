package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Lo que la aplicacion necesita para LEER usuarios. No sabe de JPA ni de SQL. */
public interface LoadUserPort {

    Optional<User> byId(UserId id);

    Optional<User> byEmail(Email email);

    /** Lectura EN LOTE, para no disparar una consulta por id (N+1). */
    List<User> byIds(Collection<UserId> ids);
}

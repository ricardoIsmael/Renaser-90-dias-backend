package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * Capa 3 de la defensa en profundidad (CLAUDE.MD §5.3.4/D-11): un actor SUSPENDIDO
 * corta antes de llegar a cualquier regla de negocio, aunque su token siga siendo
 * valido. Colaborador compartido por los servicios de `users` que cargan un actor
 * real desde un comando (antes tres copias identicas de este mismo chequeo).
 */
@Component
class RequireActiveUserGuard {

    private final LoadUserPort loadUserPort;

    RequireActiveUserGuard(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    User of(UserId id) {
        User user = loadUserPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
        if (!user.hasAccess()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return user;
    }
}

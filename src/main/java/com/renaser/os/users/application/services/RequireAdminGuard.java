package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import org.springframework.stereotype.Component;

/**
 * Gate de ADMIN/ALCHEMIST para los paneles admin nuevos (staff #6, aprendices #7,
 * solicitudes de cuenta #9). FAIL-CLOSED a proposito, nunca lanza {@link
 * java.util.NoSuchElementException} para un actor inexistente (docs/BITACORA_ERRORES.md
 * E-42): en toda operacion que apunta a UN RECURSO por id, el recurso se carga PRIMERO
 * (404 si no existe) y este guard se invoca DESPUES (403 si el actor no califica) — asi
 * un actor invalido siempre cae a 403, nunca a un 404 con mensaje distinto que delataria,
 * por comparacion, si el recurso existia o no. Mismo criterio que
 * {@code PublicacionMuroService.esModerador}/{@code requireModerador}.
 */
@Component
class RequireAdminGuard {

    private final LoadUserPort loadUserPort;

    RequireAdminGuard(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    boolean esAdminActivo(UserId actorId) {
        return loadUserPort.byId(actorId)
                .map(actor -> actor.hasAccess() && actor.canManageRoles())
                .orElse(false);
    }

    void requireAdminActivo(UserId actorId) {
        if (!esAdminActivo(actorId)) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran este panel");
        }
    }
}

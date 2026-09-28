package com.renaser.os.onboarding.application.services.caja;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

/**
 * Quién puede tocar la caja (D-219, spec §3). «Solo el Admin» opera: ADMIN con la cuenta activa, y nadie
 * más — ni ALCHEMIST ni MENTOR_LEAD. Lo exige el servicio y no solo {@code @RequiresPermission}
 * ({@code MANAGE_RENASER_BOX}): para MENTOR, ADMIN y ALCHEMIST el interceptor todavía deja pasar todo (A-1),
 * así que el 403 de un MENTOR, de un ALCHEMIST y de un ADMIN suspendido sale de acá (patrón de
 * {@code MANAGE_WELCOME}, D-210). Primero la cuenta, después el rol.
 */
@Component
class GuardiaDeCaja {

    private final UserSummaryFinder usuarios;

    GuardiaDeCaja(UserSummaryFinder usuarios) {
        this.usuarios = usuarios;
    }

    /** @throws NotAuthorizedException si la cuenta no existe, no está activa o no es ADMIN (403) */
    void exigirAdmin(UserId actorId) {
        if (exigirActiva(actorId).role() != UserRole.ADMIN) {
            throw new NotAuthorizedException("Solo el Admin lleva la Caja Renaser");
        }
    }

    /** @throws NotAuthorizedException si la cuenta no existe o no está activa (403) */
    UserSummary exigirActiva(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (!actor.status().allowsAccess()) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }
}

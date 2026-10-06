package com.renaser.os.phasecontracts.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

/**
 * Quién puede cambiar los animales de fase (D-258): ADMIN y ALCHEMIST con la cuenta activa, y nadie más.
 * Lo exige el servicio además de {@code @RequiresPermission(MANAGE_PHASE_ANIMALS)}: para ADMIN, ALCHEMIST
 * y MENTOR el interceptor todavía deja pasar todo (A-1), así que el 403 de un MENTOR sale de acá.
 */
@Component
class GuardiaDeAnimalesDeFase {

    private final UserSummaryFinder usuarios;

    GuardiaDeAnimalesDeFase(UserSummaryFinder usuarios) {
        this.usuarios = usuarios;
    }

    void exigirQuePuedaCambiarlos(UserId actorId) {
        UserSummary actor = usuarios.findById(actorId)
                .orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!actor.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo Administración y Alquimista pueden cambiar los animales de las fases");
        }
    }
}

package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

/**
 * Quién edita y consulta los términos (spec 027, FR-001, decisión Q8-A): Administrador y Alquimista con la cuenta activa, y nadie más. Lo exige el servicio
 * y no solo {@code @RequiresPermission}: para ADMIN, ALCHEMIST y MENTOR la matriz de permisos todavía deja pasar (A-1), así que el 403 de un mentor, de un
 * líder o de una cuenta suspendida sale de acá. Primero la cuenta, después el rol.
 */
@Component
class GuardiaDeTerminos {

    private final UserSummaryFinder usuarios;

    GuardiaDeTerminos(UserSummaryFinder usuarios) {
        this.usuarios = usuarios;
    }

    /** @throws NotAuthorizedException (403) si la cuenta no existe, está suspendida o no es de Administración o Alquimista */
    void exigirAdministracion(UserId actor) {
        UserSummary cuenta = usuarios.findById(actor).orElseThrow(() -> new NotAuthorizedException("Cuenta inexistente o suspendida"));
        if (!cuenta.status().allowsAccess()) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (cuenta.role() != UserRole.ADMIN && cuenta.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo Administración y Alquimista editan los términos y condiciones");
        }
    }
}

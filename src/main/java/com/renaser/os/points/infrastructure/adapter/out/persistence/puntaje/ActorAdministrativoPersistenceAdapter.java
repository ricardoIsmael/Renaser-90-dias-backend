package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import com.renaser.os.points.application.ports.out.puntaje.VerificarActorAdministrativoPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Delega en el contrato publico de `users` (D-41): quien es administrativo lo define
 * `users`, no una query de `points` contra su tabla.
 */
@Component
class ActorAdministrativoPersistenceAdapter implements VerificarActorAdministrativoPort {

    private static final Set<UserRole> ROLES_ADMINISTRATIVOS = EnumSet.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final UserSummaryFinder userSummaryFinder;

    ActorAdministrativoPersistenceAdapter(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    public boolean esAdministrativoActivo(UserId actorId) {
        return userSummaryFinder.findById(actorId)
                .filter(resumen -> resumen.status() == UserStatus.ACTIVE)
                .map(UserSummary::role)
                .filter(ROLES_ADMINISTRATIVOS::contains)
                .isPresent();
    }
}

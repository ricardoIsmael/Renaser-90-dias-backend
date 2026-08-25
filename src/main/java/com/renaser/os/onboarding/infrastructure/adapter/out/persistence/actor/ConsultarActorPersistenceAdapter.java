package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.actor;

import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delega en el contrato publico de `users` (D-41: ningun modulo lee la tabla de otro de
 * frente) — mismo patron que `phasecontracts.ConsultarProgresoParticipantePersistenceAdapter`.
 */
@Component
class ConsultarActorPersistenceAdapter implements ConsultarActorPort {

    private final UserSummaryFinder userSummaryFinder;

    ConsultarActorPersistenceAdapter(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    public Optional<ActorOnboarding> deActor(UserId actorId) {
        return userSummaryFinder.findById(actorId).map(ConsultarActorPersistenceAdapter::aActor);
    }

    private static ActorOnboarding aActor(UserSummary resumen) {
        return new ActorOnboarding(resumen.id(), !resumen.status().allowsAccess());
    }
}

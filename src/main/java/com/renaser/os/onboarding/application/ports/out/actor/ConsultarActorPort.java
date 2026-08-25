package com.renaser.os.onboarding.application.ports.out.actor;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

/**
 * Puerto LOCAL para verificar que el actor (el propio usuario, ver docs/MODULO_ONBOARDING.md
 * — este modulo no tiene el concepto de "actuar en nombre de otro") existe y no esta
 * suspendido. El adaptador delega en {@code users.api.UserSummaryFinder} (D-41: ningun
 * modulo consulta la tabla `usuarios` de frente) — mismo patron que
 * `phasecontracts.ConsultarProgresoParticipantePersistenceAdapter`.
 */
public interface ConsultarActorPort {

    Optional<ActorOnboarding> deActor(UserId actorId);

    record ActorOnboarding(UserId id, boolean suspendido) {
    }
}

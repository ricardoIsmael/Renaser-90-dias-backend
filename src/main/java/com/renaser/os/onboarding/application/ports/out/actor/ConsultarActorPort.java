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

    /** {@code nombre} es el nombre completo, para precargarlo en el diagnóstico y nombrar a la persona en su ficha; {@code administra} es Administración o Alquimista. */
    record ActorOnboarding(UserId id, boolean suspendido, String nombre, boolean administra) {

        public ActorOnboarding(UserId id, boolean suspendido) {
            this(id, suspendido, null, false);
        }
    }
}

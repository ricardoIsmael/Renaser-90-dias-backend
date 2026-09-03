package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Ya no consulta ningun puerto de IA: el veredicto de las 6 Ps se saco del alcance
 * (2026-09-03). Lo unico que queda es el guard de actor, que no es una validacion del texto
 * sino de quien lo manda.
 */
@Service
class ValidarMetaMaestraService implements ValidarMetaMaestraUseCase {

    private final ConsultarActorPort actorPort;

    ValidarMetaMaestraService(ConsultarActorPort actorPort) {
        this.actorPort = actorPort;
    }

    @Override
    public void aceptar(ValidarMetaMaestraCommand command) {
        requireActorActivo(command.actorId());
    }

    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}

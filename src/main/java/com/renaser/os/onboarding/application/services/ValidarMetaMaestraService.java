package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase;
import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ResultadoMetaMaestra.Veredicto;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.metamaestra.ValidacionMetaMaestraPort;
import com.renaser.os.onboarding.application.ports.out.metamaestra.ValidacionMetaMaestraPort.ResultadoValidacionMetaMaestra;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
class ValidarMetaMaestraService implements ValidarMetaMaestraUseCase {

    private final ValidacionMetaMaestraPort validacionPort;
    private final ConsultarActorPort actorPort;

    ValidarMetaMaestraService(ValidacionMetaMaestraPort validacionPort, ConsultarActorPort actorPort) {
        this.validacionPort = validacionPort;
        this.actorPort = actorPort;
    }

    @Override
    public ResultadoMetaMaestra validar(ValidarMetaMaestraCommand command) {
        requireActorActivo(command.actorId());
        ResultadoValidacionMetaMaestra resultado = validacionPort.validar(command.texto());
        return switch (resultado.estado()) {
            case APROBADA -> new ResultadoMetaMaestra(Veredicto.APROBADA, resultado.pesFaltantes(),
                    resultado.feedback());
            case RECHAZADA -> new ResultadoMetaMaestra(Veredicto.RECHAZADA, resultado.pesFaltantes(),
                    resultado.feedback());
            // Fail-open: nunca se trata como rechazo, ver javadoc de Veredicto.PENDIENTE_DE_REVISION.
            case NO_DISPONIBLE -> new ResultadoMetaMaestra(Veredicto.PENDIENTE_DE_REVISION, List.of(), "");
        };
    }

    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}

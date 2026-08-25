package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.OnboardingEstadoFinder;
import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.AvanzarEstadoUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.CompletarOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.ObtenerEstadoOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class EstadoOnboardingService implements ObtenerEstadoOnboardingUseCase, AvanzarEstadoUseCase,
        AceptarHitoOnboardingUseCase, CompletarOnboardingUseCase, OnboardingEstadoFinder {

    private final LoadEstadoOnboardingPort loadEstadoPort;
    private final SaveEstadoOnboardingPort saveEstadoPort;
    private final ConsultarActorPort actorPort;
    private final Clock clock;

    public EstadoOnboardingService(LoadEstadoOnboardingPort loadEstadoPort, SaveEstadoOnboardingPort saveEstadoPort,
                                    ConsultarActorPort actorPort, Clock clock) {
        this.loadEstadoPort = loadEstadoPort;
        this.saveEstadoPort = saveEstadoPort;
        this.actorPort = actorPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EstadoOnboarding obtener(UserId usuarioId) {
        requireActorActivo(usuarioId);
        return loadEstadoPort.deUsuario(usuarioId)
                .orElseGet(() -> saveEstadoPort.guardar(EstadoOnboarding.iniciar(usuarioId, clock)));
    }

    @Override
    @Transactional
    public EstadoOnboarding avanzar(AvanzarEstadoCommand command) {
        requireActorActivo(command.usuarioId());
        EstadoOnboarding estado = requireEstado(command.usuarioId());
        estado.avanzar(command.flujo(), command.seccion(), command.paso(), command.progresoFlujoJson(), clock);
        return saveEstadoPort.guardar(estado);
    }

    @Override
    @Transactional
    public EstadoOnboarding aceptar(AceptarHitoCommand command) {
        requireActorActivo(command.usuarioId());
        EstadoOnboarding estado = requireEstado(command.usuarioId());
        estado.aceptarHito(command.hito(), clock);
        return saveEstadoPort.guardar(estado);
    }

    @Override
    @Transactional
    public EstadoOnboarding completar(CompletarOnboardingCommand command) {
        requireActorActivo(command.usuarioId());
        EstadoOnboarding estado = requireEstado(command.usuarioId());
        estado.marcarCompletado(clock);
        return saveEstadoPort.guardar(estado);
    }

    @Override
    public boolean completado(UserId usuarioId) {
        return loadEstadoPort.deUsuario(usuarioId).map(EstadoOnboarding::completado).orElse(false);
    }

    @Override
    public boolean pactoFase1Firmado(UserId usuarioId) {
        return loadEstadoPort.deUsuario(usuarioId).map(e -> e.pactoFirmadoEn() != null).orElse(false);
    }

    /** Si todavia no tiene fila, la crea — mismo criterio que {@link #obtener}: el onboarding arranca solo. */
    private EstadoOnboarding requireEstado(UserId usuarioId) {
        return loadEstadoPort.deUsuario(usuarioId)
                .orElseGet(() -> EstadoOnboarding.iniciar(usuarioId, clock));
    }

    /** SUSPENDIDO -> 403. Actor inexistente -> 404. Onboarding es self-service: no hay chequeo de rol. */
    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}

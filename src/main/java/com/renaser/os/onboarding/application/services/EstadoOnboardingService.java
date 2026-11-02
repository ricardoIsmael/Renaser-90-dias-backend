package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.OnboardingEstadoFinder;
import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.AvanzarEstadoUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.CompletarOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.estado.ObtenerEstadoOnboardingUseCase;
import com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase;
import com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase.PedidoDeAceptacion;
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
import java.util.Optional;

@Service
public class EstadoOnboardingService implements ObtenerEstadoOnboardingUseCase, AvanzarEstadoUseCase,
        AceptarHitoOnboardingUseCase, CompletarOnboardingUseCase, OnboardingEstadoFinder {

    private final LoadEstadoOnboardingPort loadEstadoPort;
    private final SaveEstadoOnboardingPort saveEstadoPort;
    private final ConsultarActorPort actorPort;
    private final RegistrarAceptacionDeTerminosUseCase aceptacionDeTerminos;
    private final Clock clock;

    public EstadoOnboardingService(LoadEstadoOnboardingPort loadEstadoPort, SaveEstadoOnboardingPort saveEstadoPort,
                                    ConsultarActorPort actorPort, RegistrarAceptacionDeTerminosUseCase aceptacionDeTerminos, Clock clock) {
        this.loadEstadoPort = loadEstadoPort;
        this.saveEstadoPort = saveEstadoPort;
        this.actorPort = actorPort;
        this.aceptacionDeTerminos = aceptacionDeTerminos;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EstadoOnboarding obtener(UserId usuarioId) {
        requireActorActivo(usuarioId);
        Optional<EstadoOnboarding> existente = loadEstadoPort.deUsuario(usuarioId);
        if (existente.isEmpty()) {
            return saveEstadoPort.guardar(EstadoOnboarding.iniciar(usuarioId, clock));
        }
        return existente.filter(estado -> estado.iniciadoEn() == null).map(this::conElInicioRegistrado)
                .orElseGet(existente::get);
    }

    /** La fila nació porque Administración le asignó la ficha (spec 026): con esta primera lectura la persona ya empezó. */
    private EstadoOnboarding conElInicioRegistrado(EstadoOnboarding estado) {
        estado.registrarInicio(clock);
        return saveEstadoPort.guardar(estado);
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
        if (command.hito() == HitoOnboarding.TERMINOS) {
            // Spec 027: la constancia de QUÉ versión aceptó va en la misma transacción que el hito; si no se puede dejar, tampoco queda aceptado.
            // Spec 039: con ella queda lo que se le mostró en «Mi aceptación» y, si firmó su cronograma de pagos, su confirmación (la de la spec 032) en este mismo estado.
            aceptacionDeTerminos.registrar(command.usuarioId(), new PedidoDeAceptacion(command.versionDeTerminosId(), command.inicioVisto(), command.huellaDelCronograma()))
                    .cronograma().ifPresent(confirmacion -> estado.confirmarCronograma(confirmacion.nombre(), confirmacion.huella(), clock));
        }
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

package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.FichaDeLaPersonaFinder;
import com.renaser.os.onboarding.api.FichaDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.ficha.RespuestasDelDiagnosticoPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * La marca «qué ficha le toca» de cada persona (spec 026). Vive en {@code estado_onboarding}: es otro dato del mismo
 * estado de ingreso, no una tabla aparte.
 */
@Service
public class FichaDeLaPersonaService implements FichaDeLaPersonaUseCase, FichaDeLaPersonaFinder {

    private final LoadEstadoOnboardingPort loadEstadoPort;
    private final SaveEstadoOnboardingPort saveEstadoPort;
    private final ConsultarActorPort actorPort;
    private final RespuestasDelDiagnosticoPort respuestasPort;
    private final EtapaOnboardingPort etapaPort;
    private final Clock clock;

    public FichaDeLaPersonaService(LoadEstadoOnboardingPort loadEstadoPort, SaveEstadoOnboardingPort saveEstadoPort,
                                   ConsultarActorPort actorPort, RespuestasDelDiagnosticoPort respuestasPort,
                                   EtapaOnboardingPort etapaPort, Clock clock) {
        this.loadEstadoPort = loadEstadoPort;
        this.saveEstadoPort = saveEstadoPort;
        this.actorPort = actorPort;
        this.respuestasPort = respuestasPort;
        this.etapaPort = etapaPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TipoDeFicha tipoDe(UserId persona) {
        return loadEstadoPort.deUsuario(persona).filter(EstadoOnboarding::conDiagnostico)
                .map(estado -> TipoDeFicha.CON_DIAGNOSTICO).orElse(TipoDeFicha.ESTANDAR);
    }

    @Override
    @Transactional
    public void asignarDiagnostico(UserId persona, UserId quien) {
        requireActiva(persona);
        if (etapaPort.flujosCompletados(persona).contains(Diagnostico.FLUJO)) {
            throw new IllegalStateException("Esta persona ya terminó su diagnóstico");
        }
        EstadoOnboarding estado = loadEstadoPort.deUsuario(persona)
                .orElseGet(() -> EstadoOnboarding.conDiagnostico(persona, quien, clock));
        estado.asignarDiagnostico(quien, clock);
        saveEstadoPort.guardar(estado);
    }

    @Override
    @Transactional
    public void quitarDiagnostico(UserId persona, UserId quien) {
        requireActiva(persona);
        EstadoOnboarding estado = loadEstadoPort.deUsuario(persona).orElse(null);
        if (estado == null || !estado.conDiagnostico()) {
            return;
        }
        estado.quitarDiagnostico(respuestasPort.hayRespuestas(persona), clock);
        saveEstadoPort.guardar(estado);
    }

    /** Inexistente -> 404; suspendida -> 403: no se le cambia la ficha a una cuenta que no puede entrar. */
    private void requireActiva(UserId persona) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(persona)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + persona));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}

package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.FichaDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.in.diagnostico.AsignarFichaUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.domain.model.estado.TipoDeFicha;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Darle el diagnóstico (o devolverle la ficha estándar) a varias personas que ya existen (026-9 B). Cada una en su propia transacción: una que no se puede
 * (ya terminó, ya respondió, cuenta suspendida) queda como «omitida» con su motivo y no frena a las demás.
 */
@Service
public class AsignarFichaService implements AsignarFichaUseCase {

    private final GuardiaDelDiagnostico guardia;
    private final FichaDeLaPersonaUseCase ficha;
    private final ConsultarActorPort actores;

    public AsignarFichaService(GuardiaDelDiagnostico guardia, FichaDeLaPersonaUseCase ficha, ConsultarActorPort actores) {
        this.guardia = guardia;
        this.ficha = ficha;
        this.actores = actores;
    }

    @Override
    public ResultadoDeAsignacion asignar(UserId actor, List<UserId> personas, TipoDeFicha tipo) {
        guardia.exigirQuePuedaAdministrar(actor);
        if (personas == null || personas.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una persona");
        }
        int asignadas = 0;
        List<Omitida> omitidas = new ArrayList<>();
        for (UserId persona : personas.stream().distinct().toList()) {
            try {
                if (tipo == TipoDeFicha.CON_DIAGNOSTICO) {
                    ficha.asignarDiagnostico(persona, actor);
                } else {
                    ficha.quitarDiagnostico(persona, actor);
                }
                asignadas++;
            } catch (IllegalStateException | IllegalArgumentException | NotAuthorizedException | java.util.NoSuchElementException e) {
                omitidas.add(new Omitida(persona, actores.deActor(persona).map(ConsultarActorPort.ActorOnboarding::nombre).orElse(null), e.getMessage()));
            }
        }
        return new ResultadoDeAsignacion(asignadas, omitidas);
    }
}

package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * Quién administra las fichas: Administración y Alquimista con la cuenta activa, y nadie más. Lo exige el servicio además de
 * {@code @RequiresPermission}: para esos roles el interceptor todavía deja pasar todo (A-1), así que el 403 de un mentor sale de acá.
 */
@Component
class GuardiaDelDiagnostico {

    private final ConsultarActorPort actores;

    GuardiaDelDiagnostico(ConsultarActorPort actores) {
        this.actores = actores;
    }

    void exigirQuePuedaAdministrar(UserId actor) {
        ConsultarActorPort.ActorOnboarding quien = actores.deActor(actor)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actor));
        if (quien.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (!quien.administra()) {
            throw new NotAuthorizedException("Solo Administración y Alquimista administran las fichas");
        }
    }
}

package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * Quién opera los pagos (spec 032): Administración y Alquimista con la cuenta activa, y nadie más —ni el mentor, ni la guía, ni el líder, ni Soporte TI—. Lo exige el servicio además de
 * {@code @RequiresPermission}: para MENTOR, TRAINEE_GUIDE, ADMIN, ALCHEMIST e IT_SUPPORT el interceptor todavía deja pasar todo (A-1), así que el 403 de un mentor o de Soporte TI sale de acá
 * (el mismo patrón de la Caja y del diagnóstico).
 */
@Component
class GuardiaDePagos {

    private final ConsultarActorPort actores;

    GuardiaDePagos(ConsultarActorPort actores) {
        this.actores = actores;
    }

    /**
     * @throws NoSuchElementException (404) si la cuenta no existe
     * @throws NotAuthorizedException (403) si está suspendida o no es de Administración ni Alquimista
     */
    void exigirQuePuedaAdministrar(UserId actor) {
        ConsultarActorPort.ActorOnboarding quien = actores.deActor(actor)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actor));
        if (quien.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (!quien.administra()) {
            throw new NotAuthorizedException("Solo Administración y Alquimista llevan los pagos");
        }
    }
}

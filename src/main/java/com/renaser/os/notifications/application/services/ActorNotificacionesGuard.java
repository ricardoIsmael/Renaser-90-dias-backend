package com.renaser.os.notifications.application.services;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

/**
 * Verificacion de actor compartida por los tres servicios del modulo. Antes de esto,
 * NINGUN endpoint de `notifications` validaba al actor (E-38, docs/BITACORA_ERRORES.md):
 * una cuenta SUSPENDIDA podia leer su bandeja, cambiar sus preferencias y registrar
 * tokens push, y un {@code X-Actor-Id} inventado devolvia 200 con bandeja vacia en vez
 * de rechazar la request — violando CLAUDE.MD §0.3.
 *
 * <p>Se extrajo a una clase propia en vez de repetir el metodo privado en los tres
 * servicios (era el unico modulo con el hueco completo, no valia la pena tres copias).
 * Nombre deliberadamente especifico del modulo: dos clases con el mismo nombre simple en
 * modulos distintos chocan como beans de Spring (E-32).
 */
@Component
class ActorNotificacionesGuard {

    private final UserSummaryFinder userSummaryFinder;

    ActorNotificacionesGuard(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    /** @throws NoSuchElementException si el actor no existe; {@link NotAuthorizedException} si esta suspendido. */
    UserSummary requireActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return actor;
    }

    /**
     * La misma pregunta que {@link #requireActivo}, pero respondida con un booleano: sirve para el
     * camino de ENTREGA (el push de {@code NotificacionService}), donde no hay nadie a quien
     * devolverle un 403 — no lo pidio un usuario — y el unico efecto posible es entregar o no
     * entregar. Convertir eso en una excepcion obligaria a atraparla dos lineas mas abajo para
     * volver a un booleano.
     *
     * <p>Un destinatario que ya no existe tampoco recibe: no es un error del envio, es que no hay
     * a quien entregarle.
     */
    boolean puedeRecibirEntregas(UserId destinatarioId) {
        return userSummaryFinder.findById(destinatarioId)
                .map(destinatario -> destinatario.status().allowsAccess())
                .orElse(false);
    }
}

package com.renaser.os.notifications.application.ports.in.tokenpush;

import com.renaser.os.shared.domain.UserId;

/**
 * Saca de circulacion TODAS las suscripciones de push de una persona, igual que
 * {@code CerrarTodasLasSesionesUseCase} saca todas sus sesiones.
 *
 * <p>Existe porque un token push es una credencial de ENTREGA, no un dato del programa: sobrevive
 * a la revocacion de la cuenta si nadie lo borra (la fila de {@code tokens_push} solo se iba por
 * CASCADE al eliminar al usuario), y entonces el telefono de alguien con la cuenta suspendida
 * sigue siendo un destino valido.
 *
 * <p>No es destructivo en la practica: la app vuelve a registrar el token en cuanto haya sesion de
 * nuevo ({@code AuthContext} lo pide cada vez que aparece un usuario, y el endpoint hace upsert por
 * token), asi que reactivar una cuenta devuelve el push solo.
 */
public interface RevocarTokensPushUseCase {

    /**
     * Idempotente: revocar lo que ya no esta no falla.
     *
     * @return cuantas suscripciones se dieron de baja, para que quede en el log cuantos destinos
     *         vivos tenia la cuenta revocada
     */
    int revocarDe(UserId usuarioId);
}

package com.renaser.os.notifications.application.ports.out.push;

import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;

/**
 * Qué pasó al intentar entregar un push a UN token.
 *
 * <p>Existe porque "best effort" no puede significar "sin saber nada". Antes el envío era
 * {@code void}: un token muerto seguía intentándose para siempre y nadie podía ver por qué un
 * mentor no recibe nada. La entrega sigue sin garantizarse; lo que cambia es que su resultado
 * deja de ser invisible (plan.md §9).
 */
public record ResultadoEnvioPush(TokenPushId tokenId, Estado estado, String detalle) {

    public enum Estado {
        /** El proveedor lo aceptó. No garantiza que haya llegado al teléfono. */
        ENTREGADO,
        /** Fallo pasajero: red, 5xx, límite de tasa. Vale reintentar con espera creciente. */
        FALLO_TEMPORAL,
        /** El proveedor dice que ese token ya no sirve. Se desactiva: reintentar es tirar trabajo. */
        TOKEN_INVALIDO,
        /** No hay transporte configurado para esa plataforma. No es culpa del token. */
        SIN_TRANSPORTE
    }

    public static ResultadoEnvioPush entregado(TokenPushId tokenId) {
        return new ResultadoEnvioPush(tokenId, Estado.ENTREGADO, null);
    }

    public static ResultadoEnvioPush temporal(TokenPushId tokenId, String detalle) {
        return new ResultadoEnvioPush(tokenId, Estado.FALLO_TEMPORAL, detalle);
    }

    public static ResultadoEnvioPush invalido(TokenPushId tokenId, String detalle) {
        return new ResultadoEnvioPush(tokenId, Estado.TOKEN_INVALIDO, detalle);
    }

    public static ResultadoEnvioPush sinTransporte(TokenPushId tokenId, String plataforma) {
        return new ResultadoEnvioPush(tokenId, Estado.SIN_TRANSPORTE,
                "No hay transporte configurado para " + plataforma);
    }

    public boolean reintentable() {
        return estado == Estado.FALLO_TEMPORAL;
    }
}

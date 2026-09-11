package com.renaser.os.notifications.application.ports.out.push;

import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;

public interface DesactivarTokenPushPort {

    /**
     * Saca de circulación un token que el proveedor rechazó por inválido. Idempotente.
     *
     * <p>Se borra en vez de marcarse: un token de push no tiene historia que valga la pena
     * conservar —es una credencial de entrega, no un hecho del programa— y dejarlo marcado
     * obligaría a filtrarlo en cada consulta para siempre.
     */
    void desactivar(TokenPushId tokenId);
}

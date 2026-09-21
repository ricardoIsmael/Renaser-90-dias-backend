package com.renaser.os.notifications.application.ports.out.tokenpush;

import com.renaser.os.shared.domain.UserId;

public interface BorrarTokensPushDeUsuarioPort {

    /**
     * Borra TODAS las suscripciones de ese usuario. Se borra en vez de marcarse, por la misma
     * razon que {@code DesactivarTokenPushPort}: un token de push no tiene historia que valga la
     * pena conservar —es una credencial de entrega, no un hecho del programa— y dejarlo marcado
     * obligaria a filtrarlo en cada consulta para siempre.
     *
     * <p>Puerto propio y no un metodo mas de {@code DesactivarTokenPushPort}: aquel es "el
     * proveedor dijo que este token ya no sirve", que es un hecho tecnico de UN token; esto es
     * "esta cuenta ya no recibe", que es una decision de seguridad sobre una persona.
     *
     * @return cuantas filas se borraron
     */
    int borrarDe(UserId usuarioId);
}

package com.renaser.os.onboarding.domain.model.caja;

import java.util.List;

/**
 * Se quiso marcar enviada una caja a la que le falta algo. Es un {@link IllegalStateException} (409, como
 * todo lo que no corresponde al estado) que además dice QUÉ falta, para que la app lo muestre.
 */
public class CajaIncompletaException extends IllegalStateException {

    private final transient List<FaltaParaEnviar> faltan;

    public CajaIncompletaException(List<FaltaParaEnviar> faltan) {
        super("Para enviarla falta: " + faltan);
        this.faltan = List.copyOf(faltan);
    }

    public List<FaltaParaEnviar> faltan() {
        return faltan;
    }
}

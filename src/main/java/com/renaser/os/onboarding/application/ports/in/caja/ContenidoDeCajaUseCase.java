package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.ContenidoDeCaja;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/** La lista de lo que lleva la caja, editable por el Admin (D-219). */
public interface ContenidoDeCajaUseCase {

    ContenidoDeCaja ver(UserId actorId);

    ContenidoDeCaja reemplazar(UserId actorId, List<PedidoDeElemento> elementos);

    /** @param valor la clave estable; vacía = sale de la etiqueta */
    record PedidoDeElemento(String valor, String etiqueta) {
    }
}

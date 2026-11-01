package com.renaser.os.onboarding.application.ports.in.pagos;

import com.renaser.os.shared.domain.UserId;

/** Yo → «Mi ingreso» (spec 032): siempre lo de quien tiene la sesión; ninguna operación lleva el id de otra persona. */
public interface MiIngresoUseCase {

    /** Los pasos y el pago anotado de la persona; {@link MiIngreso#sinNada()} si no hay nada. */
    MiIngreso ver(UserId persona);

    /**
     * La persona confirma su cronograma. Idempotente: si ya confirmó este mismo cronograma no cambia nada.
     *
     * @throws IllegalArgumentException (400) si no marcó que lo revisó o el nombre falta
     * @throws IllegalStateException    (409) si no tiene un cronograma que confirmar
     */
    MiIngreso confirmar(UserId persona, ConfirmacionPedida pedido);

    /** @param revisado la casilla «Revisé mis fechas y montos.» */
    record ConfirmacionPedida(String nombre, boolean revisado) {
    }
}

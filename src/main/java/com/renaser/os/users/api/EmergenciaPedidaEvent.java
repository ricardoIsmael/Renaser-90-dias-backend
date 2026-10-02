package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * Un aprendiz pidió ayuda por una emergencia y quiere volver a un día del programa (D-244). Se publica en la
 * misma transacción que guarda el pedido: el outbox de Modulith lo reentrega si un oyente falla.
 *
 * <p>Oyentes: {@code chat} (el mensaje en su chat de soporte) y {@code notifications} (el aviso a quienes
 * atienden soporte). Los dos son idempotentes por {@code solicitudId}.
 *
 * @param queOcurrio lo que escribió la persona. Va al chat de soporte; el push NO lo lleva (sale en la
 *                   pantalla bloqueada de quien atiende)
 * @param diaAlPedir el día que vivía al pedirlo
 */
public record EmergenciaPedidaEvent(UUID solicitudId, UserId aprendizId, String queOcurrio, int diaPedido,
                                    int diaAlPedir) {

    public EmergenciaPedidaEvent {
        Objects.requireNonNull(solicitudId, "solicitudId");
        Objects.requireNonNull(aprendizId, "aprendizId");
    }
}

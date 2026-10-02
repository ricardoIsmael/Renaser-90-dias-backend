package com.renaser.os.chat.application.ports.in.emergencia;

import com.renaser.os.users.api.EmergenciaPedidaEvent;

/**
 * El pedido de emergencia de un aprendiz como mensaje del programa en su chat de soporte (D-244). Lo usa solo
 * el oyente del evento.
 */
public interface AvisarEmergenciaEnSoporteUseCase {

    /** Sin efecto si todavía no tiene chat de soporte (el aviso a quienes atienden sale igual). Idempotente. */
    void avisar(EmergenciaPedidaEvent pedido);
}

package com.renaser.os.chat.application.ports.in.emergencia;

import com.renaser.os.users.api.EmergenciaPedidaEvent;
import com.renaser.os.users.api.EmergenciaResueltaEvent;

/**
 * El pedido de emergencia de un aprendiz como mensaje del programa en su chat de soporte (D-244). Lo usa solo
 * el oyente del evento.
 */
public interface AvisarEmergenciaEnSoporteUseCase {

    /** Sin efecto si todavía no tiene chat de soporte (el aviso a quienes atienden sale igual). Idempotente. */
    void avisar(EmergenciaPedidaEvent pedido);

    /**
     * Le escribe a la persona que su pedido se resolvió («Listo: volviste al día N…» o «Revisamos tu pedido…»),
     * con el push del chat solo para ella. Sin chat de soporte, nada. Idempotente.
     */
    void responder(EmergenciaResueltaEvent resuelta);
}

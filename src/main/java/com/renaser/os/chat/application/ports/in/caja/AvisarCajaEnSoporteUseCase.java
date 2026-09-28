package com.renaser.os.chat.application.ports.in.caja;

import com.renaser.os.onboarding.api.AvisoDeCajaEvent;

/**
 * El aviso de la Caja Renaser como mensaje del programa en el chat de soporte del aprendiz (D-219, spec §5:
 * «En los 2», la bandeja y el chat). Lo usa solo el oyente del evento.
 */
public interface AvisarCajaEnSoporteUseCase {

    /** Sin efecto si el aviso no es para el aprendiz o si todavía no tiene su chat de soporte. */
    void avisar(AvisoDeCajaEvent aviso);
}

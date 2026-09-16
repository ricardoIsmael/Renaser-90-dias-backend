package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.IncorporarUsuarioAlSoporteUseCase;
import com.renaser.os.users.api.RolDeUsuarioCambiadoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Cuando alguien PASA a ser administrador o alquimista, entra a los chats de soporte que ya
 * existen — D-136. Sin esto, un ascenso dejaba a la persona viendo solo las conversaciones de los
 * aprendices que entraran despues de su ascenso.
 *
 * <p>El listener no decide nada: le pasa el usuario al caso de uso y es el dominio el que mira el
 * rol de AHORA y resuelve que corresponde (un descenso a mentor, por ejemplo, no hace nada — pero
 * tampoco lo saca de donde ya estaba: irse es una decision de la persona, no un efecto colateral de
 * un cambio de rol que nadie confirmo que deba expulsar a nadie).
 */
@Component
class RolDeUsuarioCambiadoSoporteListener {

    private final IncorporarUsuarioAlSoporteUseCase incorporarUseCase;

    RolDeUsuarioCambiadoSoporteListener(IncorporarUsuarioAlSoporteUseCase incorporarUseCase) {
        this.incorporarUseCase = incorporarUseCase;
    }

    @ApplicationModuleListener
    void on(RolDeUsuarioCambiadoEvent event) {
        incorporarUseCase.incorporar(event.usuarioId());
    }
}

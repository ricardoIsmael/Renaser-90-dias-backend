package com.renaser.os.chat.application.ports.in.conversacion;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

/**
 * Si la funcionalidad del chat que corresponde a una conversación está prendida para esa persona (spec 002): el
 * tipo de la conversación decide la clave ({@code chat.grupos}, {@code chat.global}, {@code chat.soporte},
 * {@code chat.directos}), y apagar {@code chat} las apaga todas. Lo usan los mensajes por REST y la suscripción en vivo,
 * que no tienen la clave en la ruta.
 */
public interface ExigirChatPrendidoUseCase {

    /** Lanza {@code FuncionalidadApagadaException} con el aviso si está apagada para esa persona. */
    void exigir(ConversacionId conversacionId, UserId actor);
}

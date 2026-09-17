package com.renaser.os.chat.application.ports.out.presencia;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Empuje en vivo de un cambio de presencia, por los MISMOS canales que los mensajes
 * ({@code chat:conversacion:*} en Redis, {@code /topic/conversaciones/{id}} en STOMP).
 *
 * <p>Se reutiliza el canal de la conversacion a proposito, en vez de abrir uno de presencia:
 * la autorizacion de suscripcion que ya existe —solo participantes activos— vale tal cual, y
 * el cliente que mira una conversacion no necesita una segunda suscripcion para saber si la
 * otra persona esta. El evento se distingue por el campo {@code event} del payload.
 */
public interface PublicarPresenciaFanoutPort {

    void publicar(UserId usuarioId, boolean enLinea, List<ConversacionId> destinos);
}

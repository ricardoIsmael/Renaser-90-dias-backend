package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Corta la ENTREGA a un socket cuya sesion HTTP ya no existe.
 *
 * <p>Es la mitad que faltaba de la revocacion. El canal de entrada
 * ({@link SubscripcionAutorizadaInterceptor}) impide registrar suscripciones nuevas con una sesion
 * muerta, pero no toca las <b>ya registradas</b>: a esas el broker les sigue escribiendo, porque
 * busca en su propio registro de suscripciones y no vuelve a preguntar nada. Sin este interceptor,
 * suspender a alguien o cerrarle las sesiones por robo de token no le quitaba el chat en vivo.
 *
 * <p>Devolver {@code null} descarta el mensaje para <i>ese</i> suscriptor sin afectar a los demas
 * ni cerrar el socket. Cerrarlo seria mas prolijo, pero el cliente reconectaria solo y el handshake
 * lo rechazaria con 403 — que es el mismo final por un camino mas ruidoso.
 */
@Component
class EntregaConSesionVivaInterceptor implements ChannelInterceptor {

    private final SesionViva sesionViva;

    EntregaConSesionVivaInterceptor(SesionViva sesionViva) {
        this.sesionViva = sesionViva;
    }

    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        String socketId = accessor == null ? null : accessor.getSessionId();
        if (socketId == null) {
            return message;
        }
        return sesionViva.sigueViva(socketId) ? message : null;
    }
}

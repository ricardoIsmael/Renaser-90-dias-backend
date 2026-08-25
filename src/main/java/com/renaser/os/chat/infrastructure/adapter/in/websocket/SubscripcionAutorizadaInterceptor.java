package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Antes de esta clase, {@code /ws} aceptaba cualquier SUBSCRIBE a
 * {@code /topic/conversaciones/{id}} sin verificar nada — un cliente sin ser participante
 * podia leer en vivo los mensajes de una conversacion ajena, aunque la capa REST del mismo
 * modulo si exige pertenencia (auditoria de seguridad, ver docs/BITACORA_ERRORES.md E-37).
 * Aca se aplica la MISMA regla que {@code MensajeService}/{@code ConversacionService}: el
 * actor debe existir, estar ACTIVE, y ser participante de la conversacion a la que se
 * suscribe.
 */
@Component
class SubscripcionAutorizadaInterceptor implements ChannelInterceptor {

    private static final String PREFIJO_TOPIC = "/topic/conversaciones/";

    private final EsParticipantePort esParticipantePort;
    private final UserSummaryFinder userSummaryFinder;

    SubscripcionAutorizadaInterceptor(EsParticipantePort esParticipantePort, UserSummaryFinder userSummaryFinder) {
        this.esParticipantePort = esParticipantePort;
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destino = accessor.getDestination();
        if (destino == null || !destino.startsWith(PREFIJO_TOPIC)) {
            throw new org.springframework.messaging.MessagingException("Destino de suscripcion no permitido");
        }
        UserId actorId = actorDeLaSesion(accessor.getSessionAttributes());
        ConversacionId conversacionId = ConversacionId.of(UUID.fromString(destino.substring(PREFIJO_TOPIC.length())));
        requireParticipanteActivo(actorId, conversacionId);
        return message;
    }

    private UserId actorDeLaSesion(@Nullable Map<String, Object> sessionAttributes) {
        Object actorId = sessionAttributes == null ? null : sessionAttributes.get(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID);
        if (!(actorId instanceof UUID uuid)) {
            throw new org.springframework.messaging.MessagingException("Sesion sin actor identificado");
        }
        return UserId.of(uuid);
    }

    private void requireParticipanteActivo(UserId actorId, ConversacionId conversacionId) {
        var actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new org.springframework.messaging.MessagingException("Actor no encontrado"));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new org.springframework.messaging.MessagingException("Cuenta suspendida");
        }
        if (!esParticipantePort.esParticipante(conversacionId, actorId)) {
            throw new org.springframework.messaging.MessagingException(
                    "No sos participante de esta conversacion");
        }
    }
}

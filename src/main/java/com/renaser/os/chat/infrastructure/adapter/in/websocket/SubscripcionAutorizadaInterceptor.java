package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
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
 * <p>El actor debe existir, estar ACTIVE, tener la sesion todavia viva, y estar autorizado a ver
 * la conversacion segun {@link com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase}.
 *
 * <blockquote><b>Corregido 2026-09-18.</b> Aca decia <i>"se aplica la MISMA regla que
 * MensajeService/ConversacionService"</i>. Dejo de ser cierto cuando esos servicios incorporaron
 * la revalidacion contra {@code PertenenciaVigentePort} y este interceptor quedo preguntandole a
 * la proyeccion {@code participantes_conversacion}: para una conversacion de grupo eso concede de
 * mas, porque quien roto conserva su fila. La afirmacion de que ya estaba cubierto es
 * probablemente la razon por la que nadie volvio a mirar. Ahora las cuatro entradas comparten un
 * unico caso de uso, asi que la equivalencia no depende de que nadie se olvide.</blockquote>
 */
@Component
class SubscripcionAutorizadaInterceptor implements ChannelInterceptor {

    private static final String PREFIJO_TOPIC = "/topic/conversaciones/";

    private final AutorizarAccesoAConversacionUseCase autorizarAcceso;
    private final UserSummaryFinder userSummaryFinder;
    private final SesionViva sesionViva;

    SubscripcionAutorizadaInterceptor(AutorizarAccesoAConversacionUseCase autorizarAcceso,
                                      UserSummaryFinder userSummaryFinder, SesionViva sesionViva) {
        this.autorizarAcceso = autorizarAcceso;
        this.userSummaryFinder = userSummaryFinder;
        this.sesionViva = sesionViva;
    }

    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        // S-4 (auditoria del 2026-09-01; cerrado 2026-09-06): el broker simple reparte a los
        // suscriptores TODO lo que llegue a /topic/**, venga de la aplicacion o directo de un
        // cliente. Sin esta guarda, cualquier socket autenticado podia publicar en la
        // conversacion de otros saltandose el caso de uso (y su persistencia y sus guardas). Un
        // cliente solo manda a /app/**; el unico que escribe en /topic es el servidor.
        if (accessor.getCommand() == StompCommand.SEND) {
            String destinoEnvio = accessor.getDestination();
            if (destinoEnvio != null && destinoEnvio.startsWith("/topic/")) {
                throw new org.springframework.messaging.MessagingException(
                        "Un cliente no publica directo en /topic; usa /app");
            }
            return message;
        }
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destino = accessor.getDestination();
        if (destino == null || !destino.startsWith(PREFIJO_TOPIC)) {
            throw new org.springframework.messaging.MessagingException("Destino de suscripcion no permitido");
        }
        UserId actorId = actorDeLaSesion(accessor.getSessionAttributes());
        requireSesionTodaviaViva(accessor);
        ConversacionId conversacionId = conversacionDelDestino(destino);
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
        if (!autorizarAcceso.puedeVer(conversacionId, actorId)) {
            throw new org.springframework.messaging.MessagingException(
                    "No sos participante de esta conversacion");
        }
    }

    /**
     * El id de conversacion sale de una porcion del destino elegida por el cliente, asi que puede
     * no ser un UUID. Se traduce a la excepcion del canal en vez de dejar salir el
     * {@code IllegalArgumentException} crudo de {@code UUID.fromString}: el rechazo tiene que ser
     * deliberado y no un efecto colateral del parseo.
     */
    private static ConversacionId conversacionDelDestino(String destino) {
        try {
            return ConversacionId.of(UUID.fromString(destino.substring(PREFIJO_TOPIC.length())));
        } catch (IllegalArgumentException noEsUnUuid) {
            throw new org.springframework.messaging.MessagingException("Destino de suscripcion no permitido");
        }
    }

    /**
     * Que la sesion HTTP siga existiendo, no solo que haya existido al abrir el socket.
     *
     * <p>Sin esto, quien tuviera un token robado conservaba el socket aunque la victima cambiara
     * la contrasena, y encima podia suscribirse a conversaciones NUEVAS despues de la revocacion.
     */
    private void requireSesionTodaviaViva(StompHeaderAccessor accessor) {
        Map<String, Object> atributos = accessor.getSessionAttributes();
        Object idSesion = atributos == null ? null : atributos.get(ActorHandshakeInterceptor.ATRIBUTO_ID_SESION);
        String socketId = accessor.getSessionId();
        if (!(idSesion instanceof String id) || socketId == null || !sesionViva.sigueViva(socketId, id)) {
            throw new org.springframework.messaging.MessagingException("Tu sesion ya no es valida");
        }
    }
}

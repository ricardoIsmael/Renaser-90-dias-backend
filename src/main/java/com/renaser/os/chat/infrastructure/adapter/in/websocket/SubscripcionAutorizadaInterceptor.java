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
 *
 * <blockquote><b>Agregado 2026-09-21.</b> Esta clase decide <i>una sola vez por suscripcion</i>, en
 * el SUBSCRIBE, y el broker no le vuelve a preguntar nunca. Eso alcanzaba mientras se creyera que
 * la autorizacion no caduca, pero caduca sola: rotar a un mentor, trasladar a un aprendiz o sacar a
 * alguien de un soporte apagan la pertenencia sin cerrar sesion ni socket. La revalidacion en cada
 * entrega vive en {@link EntregaAutorizadaInterceptor}; esta guarda sigue siendo la de la puerta,
 * no la unica.</blockquote>
 */
@Component
class SubscripcionAutorizadaInterceptor implements ChannelInterceptor {

    private final AutorizarAccesoAConversacionUseCase autorizarAcceso;
    private final UserSummaryFinder userSummaryFinder;
    private final SesionViva sesionViva;
    private final AutorizacionViva autorizacionViva;

    SubscripcionAutorizadaInterceptor(AutorizarAccesoAConversacionUseCase autorizarAcceso,
                                      UserSummaryFinder userSummaryFinder, SesionViva sesionViva,
                                      AutorizacionViva autorizacionViva) {
        this.autorizarAcceso = autorizarAcceso;
        this.userSummaryFinder = userSummaryFinder;
        this.sesionViva = sesionViva;
        this.autorizacionViva = autorizacionViva;
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
        if (!DestinoDeConversacion.loEs(destino)) {
            throw new org.springframework.messaging.MessagingException("Destino de suscripcion no permitido");
        }
        UserId actorId = actorDeLaSesion(accessor.getSessionAttributes());
        String socketId = requireSesionTodaviaViva(accessor);
        // El parseo del id vive en DestinoDeConversacion para que el canal de salida lea EXACTAMENTE
        // la misma conversacion en el mismo destino; aca solo cambia que se hace con el rechazo.
        ConversacionId conversacionId = DestinoDeConversacion.de(destino)
                .orElseThrow(() -> new org.springframework.messaging.MessagingException(
                        "Destino de suscripcion no permitido"));
        requireParticipanteActivo(actorId, conversacionId);
        // Recien aca, con la suscripcion ya autorizada: el canal de SALIDA necesita saber de quien
        // es este socket para poder volver a preguntar en cada entrega, y este frame es el ultimo
        // que trae los atributos del handshake — el de entrega ya no los trae.
        autorizacionViva.anotarActorDelSocket(socketId, actorId);
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
     * Que la sesion HTTP siga existiendo, no solo que haya existido al abrir el socket. Devuelve el
     * id del socket, que ya quedo comprobado no nulo.
     *
     * <p>Sin esto, quien tuviera un token robado conservaba el socket aunque la victima cambiara
     * la contrasena, y encima podia suscribirse a conversaciones NUEVAS despues de la revocacion.
     */
    private String requireSesionTodaviaViva(StompHeaderAccessor accessor) {
        Map<String, Object> atributos = accessor.getSessionAttributes();
        Object idSesion = atributos == null ? null : atributos.get(ActorHandshakeInterceptor.ATRIBUTO_ID_SESION);
        String socketId = accessor.getSessionId();
        if (!(idSesion instanceof String id) || socketId == null || !sesionViva.sigueViva(socketId, id)) {
            throw new org.springframework.messaging.MessagingException("Tu sesion ya no es valida");
        }
        return socketId;
    }
}

package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Corta la ENTREGA a un socket que ya no deberia estar recibiendo esa conversacion.
 *
 * <p>Es la mitad que faltaba de la revocacion. El canal de entrada
 * ({@link SubscripcionAutorizadaInterceptor}) impide registrar suscripciones nuevas con una sesion
 * muerta o sin pertenencia, pero no toca las <b>ya registradas</b>: a esas el broker les sigue
 * escribiendo, porque busca en su propio registro de suscripciones y no vuelve a preguntar nada.
 *
 * <p>Son dos preguntas y hacen falta las dos, porque se revoca de dos maneras distintas:
 * <ul>
 *   <li>{@link SesionViva} — <i>¿la sesion sigue existiendo?</i> Cubre suspender a alguien, el robo
 *       de token y el cambio de contrasena, que es lo que {@code cerrarTodas()} hace.</li>
 *   <li>{@link AutorizacionViva} — <i>¿sigue autorizado a ESTE destino?</i> Cubre lo que
 *       {@code cerrarTodas()} nunca toca: rotar a un mentor fuera de su grupo, trasladar a un
 *       aprendiz, sacar a alguien de un chat de soporte. Ninguno de esos caminos cierra sesiones ni
 *       sockets —apagan la pertenencia y nada mas—, asi que sin esta pregunta el ex integrante
 *       seguia recibiendo en vivo, con el texto completo, todo lo que escribieran los que se
 *       quedaron; por REST el mismo pedido le responde 403.</li>
 * </ul>
 *
 * <p><b>Antes se llamaba {@code EntregaConSesionVivaInterceptor}</b>, cuando la sesion era lo unico
 * que miraba. El nombre cambio junto con la pregunta: un control que dice en su nombre menos de lo
 * que decide es la forma en que este modulo se dejo el agujero anterior abierto sin que nadie
 * volviera a mirarlo.
 *
 * <p>Devolver {@code null} descarta el mensaje para <i>ese</i> suscriptor sin afectar a los demas
 * ni cerrar el socket. Cerrarlo seria mas prolijo cuando lo revocado es la sesion, pero el cliente
 * reconectaria solo y el handshake lo rechazaria con 403 — el mismo final por un camino mas
 * ruidoso. Cuando lo revocado es la pertenencia ni siquiera seria correcto: la persona sigue siendo
 * un usuario legitimo con otras conversaciones abiertas en el mismo socket, y lo que hay que
 * cortarle es esta, no el chat entero.
 */
@Component
class EntregaAutorizadaInterceptor implements ChannelInterceptor {

    private final SesionViva sesionViva;
    private final AutorizacionViva autorizacionViva;

    EntregaAutorizadaInterceptor(SesionViva sesionViva, AutorizacionViva autorizacionViva) {
        this.sesionViva = sesionViva;
        this.autorizacionViva = autorizacionViva;
    }

    /**
     * <b>Las cabeceras se leen del mapa, no de un accessor de un tipo concreto</b>, y esa linea es
     * la que decide si esta guarda existe o no. El frame de entrega NO lo arma un
     * {@code StompHeaderAccessor}: {@code SimpleBrokerMessageHandler.sendMessageToSubscribers} crea
     * un {@code SimpMessageHeaderAccessor} por suscriptor, asi que
     * {@code MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)} devuelve
     * {@code null} para justo los mensajes que hay que filtrar — y con el {@code null} el
     * interceptor los dejaba pasar todos. Pasaba desapercibido porque el unico test armaba el frame
     * a mano con {@code StompHeaderAccessor.create(MESSAGE)}, que es lo que el broker no hace.
     */
    @Override
    @Nullable
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        MessageHeaders cabeceras = message.getHeaders();
        String socketId = SimpMessageHeaderAccessor.getSessionId(cabeceras);
        if (socketId == null) {
            // Sin socket destinatario no hay entrega que autorizar: un frame asi no llega a
            // escribirse en ninguna conexion.
            return message;
        }
        if (!sesionViva.sigueViva(socketId)) {
            return null;
        }
        String destino = SimpMessageHeaderAccessor.getDestination(cabeceras);
        if (!DestinoDeConversacion.loEs(destino)) {
            // CONNECTED, RECEIPT, ERROR: frames del propio socket, sin conversacion que autorizar.
            // Ningun otro destino llega hasta aca: lo unico que se publica es
            // /topic/conversaciones/{id} (RedisChatSubscriberConfig) y el canal de entrada rechaza
            // todo SUBSCRIBE fuera de ese prefijo, asi que no hay suscripcion a ninguna otra cosa.
            return message;
        }
        Optional<ConversacionId> conversacionId = DestinoDeConversacion.de(destino);
        if (conversacionId.isEmpty()) {
            // Con el prefijo pero sin un id que se pueda leer: no se entrega. No deberia pasar
            // —tampoco se puede suscribir a eso—, y si pasara, que el destino no se entienda no
            // puede ser la razon por la que algo se entrega.
            return null;
        }
        return autorizacionViva.puedeRecibir(socketId, conversacionId.get()) ? message : null;
    }
}

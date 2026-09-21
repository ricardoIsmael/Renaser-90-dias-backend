package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responde si un socket STOMP <b>sigue autorizado</b> a recibir una conversacion.
 *
 * <p><b>El agujero que cierra.</b> {@link SesionViva} cerro la mitad de la revocacion que depende
 * de matar la sesion —suspension, robo de token, cambio de contrasena—, y la suscripcion se
 * autoriza una vez en {@link SubscripcionAutorizadaInterceptor}. Faltaba la otra mitad: la
 * pertenencia caduca sola, <i>sin tocar la sesion ni el socket</i>. Rotar a un mentor fuera de su
 * grupo ({@code RotacionService}), trasladar a un aprendiz ({@code TrasladoService}) o sacar a
 * alguien de un chat de soporte apagan la pertenencia en el acto, pero ninguno cierra sesiones ni
 * cancela suscripciones — {@code cerrarTodas()} tiene exactamente dos llamadores y no es ninguno de
 * esos. El broker, que no vuelve a preguntar nada despues del SUBSCRIBE, le seguia entregando a esa
 * persona el texto completo de cada mensaje nuevo. Por REST la misma persona recibe 403.
 *
 * <p><b>La pregunta es la misma que la del REST</b>, y a proposito: se delega en
 * {@link AutorizarAccesoAConversacionUseCase}, el unico lugar donde vive la regla desde que se
 * dejaron de mantener cuatro copias. Asi la entrega en vivo hereda gratis lo que ese caso de uso ya
 * sabe —pertenencia vigente para un grupo, rol de staff vigente para un soporte— y no se
 * desincroniza la proxima vez que la regla cambie.
 *
 * <p><b>Por que con memoria y no consultando en cada entrega.</b> Igual que {@link SesionViva}, el
 * canal de salida pasa por aca una vez por mensaje <i>y por suscriptor</i>, y esta pregunta es
 * bastante mas cara que aquella: para un grupo son tres consultas a Postgres (la conversacion, la
 * celula y sus asignaciones). En un grupo de diez, sin memoria, cada mensaje costaria treinta. La
 * respuesta se reusa durante {@link SesionViva#GRACIA} por (socket, conversacion), que deja el
 * costo en tres consultas por suscriptor cada diez segundos y acota la ventana de exposicion tras
 * revocar a ese mismo lapso — el mismo trato que ya se acepto para la sesion. Se recuerda tambien
 * el {@code false}: si no, al que le acaban de revocar la pertenencia le costaria una consulta por
 * cada mensaje que escriban los que se quedaron, que es justo el caso que mas duele.
 *
 * <p><b>Falla CERRADO</b>, al reves que {@link SesionViva}. Si la consulta no se puede resolver, no
 * se entrega: perder un mensaje en vivo se recupera solo —el cliente lo trae por REST, que
 * revalida igual— mientras que entregarlo de mas no se deshace. El error no se recuerda, para que
 * la primera consulta que vuelva a funcionar restablezca la entrega sin esperar la gracia.
 */
@Component
class AutorizacionViva {

    private static final Logger log = LoggerFactory.getLogger(AutorizacionViva.class);

    private record Decision(long enMillis, boolean autorizado) {}

    /**
     * Lo que se sabe de un socket: quien es, y que se le contesto ultimamente sobre cada
     * conversacion. Van juntos en una sola entrada para que desconectarse los borre de una, sin
     * dejar decisiones huerfanas de un socket que ya no existe.
     */
    private record Socket(UserId actor, Map<ConversacionId, Decision> decisiones) {}

    private final AutorizarAccesoAConversacionUseCase autorizarAcceso;
    private final Clock clock;

    /** Socket STOMP -> lo que se sabe de el. Solo de ESTA instancia; se limpia al desconectarse. */
    private final Map<String, Socket> porSocket = new ConcurrentHashMap<>();

    AutorizacionViva(AutorizarAccesoAConversacionUseCase autorizarAcceso, Clock clock) {
        this.autorizarAcceso = autorizarAcceso;
        this.clock = clock;
    }

    /**
     * Quien es el actor de un socket, anotado por el canal de ENTRADA al autorizar un SUBSCRIBE.
     *
     * <p><b>Por que no sale del frame de entrega.</b> El broker arma cada copia por su cuenta y no
     * arrastra los atributos del handshake del suscriptor, asi que en la entrega no hay de donde
     * sacar al actor. Hay que haberlo anotado antes.
     *
     * <p><b>Y por que no se anota en {@code SessionConnectedEvent}</b>, que seria lo natural y es
     * lo que hacen {@link SesionViva} y {@code PresenciaDeSockets}: <b>ese evento no trae los
     * atributos del handshake</b>. Lo que viaja en el es el CONNECT_ACK que arma
     * {@code SimpleBrokerMessageHandler}, con tres cabeceras y ninguna es {@code
     * simpSessionAttributes} —la conversion a CONNECTED ocurre despues y solo para el cable—, de
     * modo que un listener de ese evento se encuentra el mapa de atributos en {@code null} y no
     * anota a nadie. Un registro asi se quedaria vacio en silencio, y como aca no figurar significa
     * no recibir, el chat en vivo se apagaria entero.
     *
     * <p>El SUBSCRIBE si los trae ({@code StompSubProtocolHandler} se los pone a cada frame del
     * cliente), y ademas es el momento exacto en que hace falta: no hay entrega que filtrar sin una
     * suscripcion previa, y esa suscripcion pasa siempre por aca.
     */
    void anotarActorDelSocket(String socketId, UserId actor) {
        porSocket.compute(socketId, (id, anotado) ->
                anotado != null && anotado.actor().equals(actor)
                        ? anotado // Otra suscripcion del mismo socket: no se tiran las respuestas ya cacheadas.
                        : new Socket(actor, new ConcurrentHashMap<>()));
    }

    @EventListener
    void alDesconectarse(SessionDisconnectEvent evento) {
        String socketId = evento.getSessionId();
        if (socketId != null) {
            porSocket.remove(socketId);
        }
    }

    /**
     * Un socket que no figura NO recibe, al reves que en {@link SesionViva#sigueViva(String)}, que
     * deja pasar al desconocido para no romper los frames del propio establecimiento de la
     * conexion. Aca no cae ninguno de esos: esta pregunta solo se hace por un destino de
     * conversacion, y a un destino asi el broker no entrega nada sin una suscripcion previa — que
     * es justo lo que anota el mapa. No figurar significa entonces que por este socket no paso
     * ningun SUBSCRIBE autorizado, y a eso no se le da contenido de nadie.
     *
     * <p>El socket y sus entregas viven siempre en la MISMA instancia —el broker simple solo
     * reparte a sus propios sockets, y el fanout entre instancias lo hace Redis—, asi que la
     * anotacion nunca queda del otro lado.
     */
    boolean puedeRecibir(String socketId, ConversacionId conversacionId) {
        Socket socket = porSocket.get(socketId);
        if (socket == null) {
            return false;
        }
        long ahora = clock.now().toEpochMilli();
        Decision ultima = socket.decisiones().get(conversacionId);
        if (ultima != null && ahora - ultima.enMillis() < SesionViva.GRACIA.toMillis()) {
            return ultima.autorizado();
        }
        boolean autorizado;
        try {
            autorizado = autorizarAcceso.puedeVer(conversacionId, socket.actor());
        } catch (RuntimeException noSePudoResolver) {
            log.warn("No se pudo resolver el acceso de {} a {}; no se entrega el mensaje",
                    socket.actor(), conversacionId, noSePudoResolver);
            return false;
        }
        socket.decisiones().put(conversacionId, new Decision(ahora, autorizado));
        return autorizado;
    }
}

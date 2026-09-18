package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.shared.domain.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Responde si la sesion HTTP detras de un socket STOMP <b>sigue existiendo</b>.
 *
 * <p><b>El agujero que cierra (2026-09-18).</b> El handshake leia la sesion de Redis una sola vez
 * y copiaba el UUID del actor a los atributos del socket; desde ese instante nadie volvia a
 * mirarla. Como {@code WebSocketConfig} tampoco usaba la integracion de Spring Session que cierra
 * el socket cuando la sesion desaparece, {@code cerrarTodas()} —el control que existe justamente
 * para el token robado y para la suspension— no alcanzaba al canal en vivo:
 *
 * <ul>
 *   <li>Suspender a alguien conectado le impedia suscribirse a algo nuevo, pero sus suscripciones
 *       ya registradas seguian entregandole todo lo que su grupo escribiera.</li>
 *   <li>Peor: quien tuviera un {@code X-Auth-Token} robado conservaba el socket <i>y podia
 *       registrar suscripciones nuevas</i> aunque la victima cambiara la contrasena — que es
 *       exactamente lo que {@code ResetContrasenaService} dice estar evitando cuando llama a
 *       {@code cerrarTodas}. El operador no tenia forma de cortarlo sin reiniciar la instancia.</li>
 * </ul>
 *
 * <p><b>Por que con memoria y no consultando Redis cada vez.</b> El canal de salida pasa por aca
 * una vez por mensaje <i>y por suscriptor</i>: en un grupo de diez, cada mensaje de chat serian
 * diez lecturas a Redis. La comprobacion se reusa durante {@link #GRACIA}, asi que el costo es una
 * lectura por socket cada diez segundos y la ventana de exposicion tras revocar queda acotada a
 * ese mismo lapso. Diez segundos de mas es una eternidad menos que "hasta que reinicien".
 */
@Component
class SesionViva {

    /** Cuanto se reusa una comprobacion antes de volver a preguntarle a Redis. */
    private static final Duration GRACIA = Duration.ofSeconds(10);

    private record Comprobacion(String idSesionHttp, long enMillis, boolean viva) {}

    private final SessionRepository<? extends Session> sessionRepository;
    private final Clock clock;

    /** Socket STOMP -> ultima comprobacion de su sesion HTTP. Se limpia al desconectarse. */
    private final Map<String, Comprobacion> porSocket = new ConcurrentHashMap<>();

    SesionViva(SessionRepository<? extends Session> sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @EventListener
    void alConectarse(SessionConnectedEvent evento) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(evento.getMessage());
        String socketId = accessor.getSessionId();
        Map<String, Object> atributos = accessor.getSessionAttributes();
        Object idSesionHttp = atributos == null ? null : atributos.get(ActorHandshakeInterceptor.ATRIBUTO_ID_SESION);
        if (socketId != null && idSesionHttp instanceof String id) {
            porSocket.put(socketId, new Comprobacion(id, clock.now().toEpochMilli(), true));
        }
    }

    @EventListener
    void alDesconectarse(SessionDisconnectEvent evento) {
        String socketId = evento.getSessionId();
        if (socketId != null) {
            porSocket.remove(socketId);
        }
    }

    /**
     * Para el canal de ENTRADA, donde el id de la sesion HTTP viene en los atributos del frame.
     * Es la comprobacion fuerte: si no hay id, no hay sesion que valga.
     */
    boolean sigueViva(String socketId, String idSesionHttp) {
        return comprobar(socketId, idSesionHttp);
    }

    /**
     * Para el canal de SALIDA, donde el broker arma el frame por su cuenta y no siempre arrastra
     * los atributos del socket suscriptor.
     *
     * <p>Un socket que no figura en el mapa se deja pasar a proposito: figuran todos desde que se
     * publica {@code SessionConnectedEvent}, asi que lo unico que cae en este hueco son los frames
     * del propio establecimiento de la conexion —el {@code CONNECTED} entre ellos—, y rechazarlos
     * romperia toda conexion legitima sin cerrarle la puerta a nadie. La suscripcion, que es lo
     * que de verdad hay que autorizar, ya paso por el canal de entrada.
     */
    boolean sigueViva(String socketId) {
        Comprobacion ultima = porSocket.get(socketId);
        return ultima == null || comprobar(socketId, ultima.idSesionHttp());
    }

    private boolean comprobar(String socketId, String idSesionHttp) {
        long ahora = clock.now().toEpochMilli();
        Comprobacion ultima = porSocket.get(socketId);
        if (ultima != null && idSesionHttp.equals(ultima.idSesionHttp())
                && ahora - ultima.enMillis() < GRACIA.toMillis()) {
            return ultima.viva();
        }
        boolean viva = existeEnRedis(idSesionHttp);
        porSocket.put(socketId, new Comprobacion(idSesionHttp, ahora, viva));
        return viva;
    }

    private boolean existeEnRedis(String idSesionHttp) {
        try {
            Session sesion = sessionRepository.findById(idSesionHttp);
            return sesion != null && !sesion.isExpired();
        } catch (RuntimeException redisCaido) {
            /* Falla ABIERTO, y es deliberado: si Redis no responde, nadie puede iniciar sesion de
               todos modos, y cortarle el chat a todo el mundo por una intermitencia es peor que
               estirar unos segundos la ventana de revocacion. El handshake, que es la puerta de
               entrada, si falla cerrado — sin Redis no se abre ningun socket nuevo. */
            return true;
        }
    }
}

package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.in.presencia.RegistrarPresenciaUseCase;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traduce sockets a personas: convierte los eventos STOMP de conexion y desconexion en
 * "fulano entro" / "fulano se fue" para {@link RegistrarPresenciaUseCase}.
 *
 * <p><b>Por que hay una cuenta y no un booleano.</b> Una misma persona puede tener dos sockets
 * abiertos —el telefono y la web, o el telefono reconectandose sin haber cerrado del todo el
 * anterior—. Si cada desconexion apagara la presencia, cerrar una pestana dejaria "ausente" a
 * alguien que sigue mirando el chat en el celular, y una reconexion normal produciria un
 * parpadeo apagado/encendido en la pantalla de la otra persona. Por eso se cuentan los sockets
 * por usuario y solo el PRIMERO enciende y el ULTIMO apaga.
 *
 * <p><b>Por que el mapa es de sesion a usuario.</b> El evento de desconexion trae el id de
 * sesion STOMP, no los atributos del handshake: si no se hubiera guardado quien era esa sesion
 * al conectarse, al cerrarse no habria forma de saber a quien apagar.
 *
 * <p>Este registro es LOCAL a la instancia, a proposito: cada backend sabe de sus propios
 * sockets y lo unico compartido es el resultado en Redis. El refresco periodico es lo que hace
 * que una instancia que muere sin avisar no deje a su gente encendida para siempre — deja de
 * refrescar y las llaves vencen solas.
 */
@Component
class PresenciaDeSockets {

    private static final Logger log = LoggerFactory.getLogger(PresenciaDeSockets.class);

    /** Sesion STOMP -> usuario. Solo de ESTA instancia. */
    private final Map<String, UUID> usuarioPorSesion = new ConcurrentHashMap<>();

    /** Usuario -> cuantos sockets suyos hay abiertos aca. Nunca queda en cero: se borra. */
    private final Map<UUID, Integer> socketsPorUsuario = new ConcurrentHashMap<>();

    private final RegistrarPresenciaUseCase registrarPresencia;

    PresenciaDeSockets(RegistrarPresenciaUseCase registrarPresencia) {
        this.registrarPresencia = registrarPresencia;
    }

    @EventListener
    void alConectarse(SessionConnectedEvent evento) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(evento.getMessage());
        String sesionId = accessor.getSessionId();
        UUID actorId = actorDe(accessor);
        if (sesionId == null || actorId == null) {
            return;
        }
        usuarioPorSesion.put(sesionId, actorId);
        boolean esElPrimero = socketsPorUsuario.merge(actorId, 1, Integer::sum) == 1;
        if (esElPrimero) {
            registrarPresencia.seConecto(UserId.of(actorId));
        }
    }

    @EventListener
    void alDesconectarse(SessionDisconnectEvent evento) {
        String sesionId = evento.getSessionId();
        if (sesionId == null) {
            return;
        }
        UUID actorId = usuarioPorSesion.remove(sesionId);
        if (actorId == null) {
            return;
        }
        // `compute` y no leer-decidir-escribir: dos sockets del mismo usuario pueden cerrarse a
        // la vez en hilos distintos, y con la version no atomica los dos podrian ver "queda 1" y
        // ninguno apagar la presencia.
        Integer restantes = socketsPorUsuario.compute(actorId,
                (id, cuenta) -> cuenta == null || cuenta <= 1 ? null : cuenta - 1);
        if (restantes == null) {
            registrarPresencia.seDesconecto(UserId.of(actorId));
        }
    }

    /**
     * Renueva el vencimiento de los que siguen conectados aca.
     *
     * <p>Cada 45 segundos contra una vigencia de 3 minutos: hay que perder tres refrescos
     * seguidos para que alguien conectado parpadee. No publica nada — renovar no es un cambio,
     * y avisarlo seria ruido en todos los sockets abiertos cada 45 segundos.
     *
     * <p><b>Sin ShedLock, al reves que el resto de los schedulers del proyecto.</b> Los demas se
     * bloquean para que una tarea corra en UNA sola instancia; esta tiene que correr en TODAS,
     * porque cada una refresca los sockets que solo ella tiene. Si se bloqueara, las instancias
     * que perdieran el lock dejarian caer la presencia de su propia gente.
     */
    @Scheduled(fixedDelay = 45_000L, initialDelay = 45_000L)
    void refrescarPresencias() {
        if (socketsPorUsuario.isEmpty()) {
            return;
        }
        for (UUID actorId : socketsPorUsuario.keySet()) {
            try {
                registrarPresencia.sigueConectado(UserId.of(actorId));
            } catch (RuntimeException e) {
                log.warn("No se pudo refrescar la presencia de {}", actorId, e);
            }
        }
    }

    private static UUID actorDe(StompHeaderAccessor accessor) {
        Map<String, Object> atributos = accessor.getSessionAttributes();
        Object actorId = atributos == null ? null : atributos.get(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID);
        return actorId instanceof UUID uuid ? uuid : null;
    }
}

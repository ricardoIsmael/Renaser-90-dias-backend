package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.chat.application.ports.in.presencia.RegistrarConversacionAbiertaUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traduce suscripciones a «tiene este chat abierto» (D-221), para no mandarle push de un mensaje que
 * ya está viendo.
 *
 * <p><b>Por qué la suscripción sirve de señal.</b> La app se suscribe a
 * {@code /topic/conversaciones/{id}} solo con esa conversación en pantalla ({@code useChatEnVivo}) y,
 * desde D-221, se desuscribe al pasar a segundo plano. La web no abre socket: ahí nunca se omite el
 * push (el service worker decide si mostrarlo con la ventana enfocada). Un APK anterior a D-221 no se
 * desuscribe en segundo plano: mientras su socket siga vivo (hasta que Android congele la app y el
 * broker lo corte por latidos, ~30 s) no recibe push de ESE chat.
 *
 * <p><b>Solo suscripciones autorizadas.</b> Spring publica {@link SessionSubscribeEvent} recién
 * después de que el SUBSCRIBE pasó por el canal de entrada, donde {@link SubscripcionAutorizadaInterceptor}
 * rechaza a quien no puede ver la conversación ({@code StompSubProtocolHandler}: {@code if (sent)
 * publishEvent(...)}).
 *
 * <p><b>Cuenta por (persona, conversación)</b>, como {@link PresenciaDeSockets}: el mismo chat abierto
 * en dos teléfonos no se cierra al cerrarlo en uno. El registro es LOCAL a la instancia; lo compartido
 * es la llave de Redis con vencimiento, renovada cada 45 s.
 */
@Component
class ConversacionesAbiertasDeSockets {

    private static final Logger log = LoggerFactory.getLogger(ConversacionesAbiertasDeSockets.class);

    private record Abierta(UUID usuarioId, ConversacionId conversacionId) {
    }

    /** Socket → (id de suscripción → lo que abrió). El UNSUBSCRIBE solo trae el id de la suscripción. */
    private final Map<String, Map<String, Abierta>> porSocket = new ConcurrentHashMap<>();

    /** Cuántas suscripciones hay abiertas en ESTA instancia por (persona, conversación). Nunca queda en cero. */
    private final Map<Abierta, Integer> cuentas = new ConcurrentHashMap<>();

    private final RegistrarConversacionAbiertaUseCase registrar;

    ConversacionesAbiertasDeSockets(RegistrarConversacionAbiertaUseCase registrar) {
        this.registrar = registrar;
    }

    @EventListener
    void alSuscribirse(SessionSubscribeEvent evento) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(evento.getMessage());
        String socketId = accessor.getSessionId();
        String suscripcionId = accessor.getSubscriptionId();
        UUID actorId = actorDe(accessor);
        var conversacion = DestinoDeConversacion.de(accessor.getDestination());
        if (socketId == null || suscripcionId == null || actorId == null || conversacion.isEmpty()) {
            return;
        }
        Abierta abierta = new Abierta(actorId, conversacion.get());
        porSocket.computeIfAbsent(socketId, s -> new ConcurrentHashMap<>()).put(suscripcionId, abierta);
        if (cuentas.merge(abierta, 1, Integer::sum) == 1) {
            intentar(() -> registrar.laAbrio(UserId.of(actorId), abierta.conversacionId()));
        }
    }

    @EventListener
    void alDesuscribirse(SessionUnsubscribeEvent evento) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(evento.getMessage());
        Map<String, Abierta> suyas = accessor.getSessionId() == null ? null : porSocket.get(accessor.getSessionId());
        if (suyas == null || accessor.getSubscriptionId() == null) {
            return;
        }
        soltar(suyas.remove(accessor.getSubscriptionId()));
    }

    @EventListener
    void alDesconectarse(SessionDisconnectEvent evento) {
        Map<String, Abierta> suyas = evento.getSessionId() == null ? null : porSocket.remove(evento.getSessionId());
        if (suyas != null) {
            suyas.values().forEach(this::soltar);
        }
    }

    /** Sin ShedLock, como {@link PresenciaDeSockets#refrescarPresencias}: cada instancia renueva lo suyo. */
    @Scheduled(fixedDelay = 45_000L, initialDelay = 45_000L)
    void renovar() {
        for (Abierta abierta : cuentas.keySet()) {
            intentar(() -> registrar.sigueAbierta(UserId.of(abierta.usuarioId()), abierta.conversacionId()));
        }
    }

    private void soltar(Abierta abierta) {
        if (abierta == null) {
            return;
        }
        // `compute` y no leer-decidir-escribir: dos sockets pueden cerrar el mismo chat a la vez.
        Integer restantes = cuentas.compute(abierta, (k, cuenta) -> cuenta == null || cuenta <= 1 ? null : cuenta - 1);
        if (restantes == null) {
            intentar(() -> registrar.laCerro(UserId.of(abierta.usuarioId()), abierta.conversacionId()));
        }
    }

    /** Redis caído no puede tumbar el socket: lo peor es un push de más o de menos durante la caída. */
    private static void intentar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            log.warn("[chat.ConversacionesAbiertasDeSockets] no se pudo anotar el chat abierto: {}", e.getMessage());
        }
    }

    private static UUID actorDe(StompHeaderAccessor accessor) {
        Map<String, Object> atributos = accessor.getSessionAttributes();
        Object actorId = atributos == null ? null : atributos.get(ActorHandshakeInterceptor.ATRIBUTO_ACTOR_ID);
        return actorId instanceof UUID uuid ? uuid : null;
    }
}

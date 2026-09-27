package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Endpoint STOMP para el chat en vivo (CLAUDE.MD del encargo: reemplaza el polling).
 * El cliente se conecta a {@code /ws} y se suscribe a
 * {@code /topic/conversaciones/{conversacionId}} para recibir mensajes en tiempo real —
 * {@link com.renaser.os.chat.infrastructure.adapter.out.redis.RedisChatSubscriberConfig}
 * es quien empuja a ese topic. Broker simple en memoria (no STOMP broker relay a
 * RabbitMQ, CLAUDE.MD §5.2.1: con Redis ya en el stack, es el punto de partida por
 * defecto) — cada instancia solo entrega a SUS propios sockets, el fanout entre
 * instancias lo hace Redis Pub/Sub, no este broker.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Latidos del broker, en los dos sentidos (D-202, 2026-09-27; propuesto en E-331).
     *
     * <p><b>Qué resuelve.</b> Sin latidos el broker contestaba {@code heart-beat:0,0} y una conexión
     * muerta (el teléfono que pasa de wifi a datos, o que se queda sin señal) no se notaba nunca: el
     * socket seguía «abierto» del lado del servidor, con sus suscripciones y su «en línea»
     * ({@code PresenciaDeSockets} lo refresca cada 45 s mientras el socket exista). Con latidos, si
     * el cliente no escribe nada en 3 × 10 s el broker cierra la sesión STOMP ({@code ERROR
     * Session closed.} + cierre del socket), y el {@code SessionDisconnectEvent} apaga la presencia.
     *
     * <p><b>Por qué 10 s y en los dos sentidos.</b> Es lo que ofrecen los dos clientes que existen
     * (`conexionStomp.ts`: {@code heart-beat:10000,10000}), así que la negociación queda en 10 s
     * para ambos. La app nueva manda su latido cada 10 s y, cuando el servidor promete latir, da
     * la conexión por muerta tras 32 s de silencio; el servidor late cada 10 s, holgado.
     *
     * <p><b>A quién podía cortar, verificado antes de activarlo</b> (frontend `origin/master` y
     * `evidencia-foto`, 2026-09-27): la web no abre este socket ({@code HAY_CHAT_EN_VIVO} es falso
     * en web); el APK publicado nunca completa el CONNECT (E-331: sus tramas salen sin el NUL), así
     * que el broker no registra su sesión y nada cambia para él; la app nueva manda latidos. Nadie
     * que lata se desconecta.
     *
     * <p><b>Costo.</b> Cada latido del servidor pasa por {@link EntregaAutorizadaInterceptor}, que
     * mira la sesión HTTP con la memoria de {@code SesionViva}: como mucho una lectura de Redis por
     * socket cada 10 s. Si la sesión se revocó, el latido no sale y el cliente, al no oír nada,
     * cierra y reconecta (el handshake lo rechaza con 403): lo mismo que ya pasaba con los mensajes.
     */
    static final long LATIDO_MS = 10_000;

    private final ActorHandshakeInterceptor actorHandshakeInterceptor;
    private final SubscripcionAutorizadaInterceptor subscripcionAutorizadaInterceptor;
    private final EntregaAutorizadaInterceptor entregaAutorizadaInterceptor;
    /**
     * Los MISMOS origenes que CORS (auditoria NFR 2026-09-06; S-6 de la auditoria del
     * 2026-09-01). Antes era {@code setAllowedOriginPatterns("*")}: cualquier pagina web podia
     * abrir el socket contra produccion desde el navegador de un aprendiz logueado. La app
     * nativa no manda {@code Origin} y Spring la deja pasar igual; la lista solo restringe a los
     * navegadores, que es donde vive el riesgo.
     */
    private final List<String> origenesPermitidos;
    /**
     * El programador que Spring ya crea para el broker ({@code messageBrokerTaskScheduler}), y no
     * uno propio: los {@code @Scheduled} de toda la app buscan un {@code TaskScheduler} ÚNICO en el
     * contexto ({@code TaskSchedulerRouter}); con un segundo bean dejarían de encontrarlo y caerían
     * a un programador local de un solo hilo. {@code @Lazy} porque ese bean vive en la misma
     * configuración que consume esta clase (así lo muestra la guía de Spring).
     */
    private final TaskScheduler programadorDelBroker;

    WebSocketConfig(ActorHandshakeInterceptor actorHandshakeInterceptor,
                     SubscripcionAutorizadaInterceptor subscripcionAutorizadaInterceptor,
                     EntregaAutorizadaInterceptor entregaAutorizadaInterceptor,
                     @Value("${renaser.web.cors.origenes}") List<String> origenesPermitidos,
                     @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler programadorDelBroker) {
        this.actorHandshakeInterceptor = actorHandshakeInterceptor;
        this.subscripcionAutorizadaInterceptor = subscripcionAutorizadaInterceptor;
        this.entregaAutorizadaInterceptor = entregaAutorizadaInterceptor;
        this.origenesPermitidos = List.copyOf(origenesPermitidos);
        this.programadorDelBroker = programadorDelBroker;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origenesPermitidos.toArray(String[]::new))
                .addInterceptors(actorHandshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {LATIDO_MS, LATIDO_MS})
                .setTaskScheduler(programadorDelBroker);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscripcionAutorizadaInterceptor);
    }

    /**
     * El canal de SALIDA existe por la mitad de la revocacion que el de entrada no cubre: a una
     * suscripcion ya registrada el broker le sigue escribiendo sin volver a preguntarle nada a
     * nadie — ni si la sesion sigue viva, ni si quien la registro sigue perteneciendo a esa
     * conversacion. Ver {@link EntregaAutorizadaInterceptor}.
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(entregaAutorizadaInterceptor);
    }
}

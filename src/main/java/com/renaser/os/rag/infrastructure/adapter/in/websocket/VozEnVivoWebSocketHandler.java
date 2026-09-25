package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.ConversacionEnVivo;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.MotivoDeCierre;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.SalidaDeVozEnVivo;
import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * El WebSocket de la voz en vivo, {@code /api/v1/renasia/voz/en-vivo} (D-162, contrato en §5.ter
 * de {@code docs/arquitectura/PROPUESTA_GEMINI_LIVE.md}).
 *
 * <p><b>Tonto a proposito</b>, como un controller: la autenticacion ya paso en el handshake
 * ({@link VozEnVivoHandshakeInterceptor}), y aca solo se traduce. Frames binarios ⇄ audio PCM 16 kHz,
 * frames de texto ⇄ eventos JSON ({@link EventoDeVozEnVivoJson}), y el cierre segun el motivo que
 * decide el caso de uso:
 *
 * <ul>
 *   <li>{@code NORMAL}: 1000.</li>
 *   <li>{@code CUOTA_AGOTADA}: 1000 con motivo {@code cuota-agotada} (antes llega {@code cuotaAgotada}).</li>
 *   <li>{@code NO_DISPONIBLE}: 1013 "intente mas tarde", motivo {@code no-disponible} (voz en vivo
 *   apagada o Gemini que no abre; antes llega {@code error}). La app vuelve al flujo anterior.</li>
 *   <li>{@code ERROR}: 1011, motivo {@code error} (antes llega {@code error}).</li>
 * </ul>
 *
 * <p>Los envios pasan por {@link ConcurrentWebSocketSessionDecorator}: escriben el hilo del
 * proveedor (audio, transcripcion) y el del temporizador (cuota), y la sesion de Spring no admite
 * dos envios a la vez.
 */
@Component
class VozEnVivoWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VozEnVivoWebSocketHandler.class);

    static final String ATRIBUTO_CONVERSACION = "conversacionEnVivo";
    private static final String ATRIBUTO_PARTIDOS = "framesPartidos";
    /** Un envio a un telefono con mala senal no puede frenar la conversacion mas que esto. */
    private static final int LIMITE_DE_ENVIO_MS = 5_000;
    /** ~16 s de audio encolado; si se pasa, el telefono no esta leyendo y se corta. */
    private static final int LIMITE_DE_BUFER = 512 * 1024;

    private final ConversarEnVivoUseCase conversarEnVivo;

    VozEnVivoWebSocketHandler(ConversarEnVivoUseCase conversarEnVivo) {
        this.conversarEnVivo = conversarEnVivo;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UserId actor = (UserId) session.getAttributes().get(VozEnVivoHandshakeInterceptor.ATRIBUTO_ACTOR);
        SalidaWebSocket salida = new SalidaWebSocket(
                new ConcurrentWebSocketSessionDecorator(session, LIMITE_DE_ENVIO_MS, LIMITE_DE_BUFER));
        ConversacionEnVivo conversacion = conversarEnVivo.iniciar(actor, salida);
        session.getAttributes().put(ATRIBUTO_CONVERSACION, conversacion);
        // Abrir la sesion con Gemini tarda unos segundos. Si la persona cerro el orbe mientras tanto,
        // nadie mas va a terminar esta conversacion: sin esto, Gemini seguia abierto y la cuota
        // corriendo hasta el tope.
        if (!session.isOpen()) {
            conversacion.terminar();
        }
    }

    /**
     * Acepta frames partidos y los junta aca (E-234). Sin esto, Tomcat rechaza con 1009 todo frame
     * de mas de 8 KB (su buffer por defecto) y la app no tiene como saberlo de antemano: 8 KB son
     * apenas 256 ms de audio. Juntarlos en el handler evita subir el limite para todo el contenedor
     * (tambien el del STOMP del chat) y mantiene el audio alineado a muestras enteras.
     */
    @Override
    public boolean supportsPartialMessages() {
        return true;
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer datos = message.getPayload();
        byte[] pedazo = new byte[datos.remaining()];
        datos.get(pedazo);
        FramesPartidos partidos = partidos(session);
        byte[] audio = partidos.juntarBinario(pedazo, message.isLast());
        if (audio != null) {
            conversacion(session).recibirAudio(audio);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String texto = partidos(session).juntarTexto(message.getPayload(), message.isLast());
        if (texto != null && EventoDeVozEnVivoJson.esFin(texto)) {
            conversacion(session).terminar();
        }
    }

    private static FramesPartidos partidos(WebSocketSession session) {
        return (FramesPartidos) session.getAttributes().computeIfAbsent(ATRIBUTO_PARTIDOS, clave -> new FramesPartidos());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        conversacion(session).terminar();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        conversacion(session).terminar();
    }

    private static ConversacionEnVivo conversacion(WebSocketSession session) {
        Object guardada = session.getAttributes().get(ATRIBUTO_CONVERSACION);
        return guardada instanceof ConversacionEnVivo conversacion ? conversacion : SinConversacion.INSTANCIA;
    }

    static CloseStatus estadoDeCierre(MotivoDeCierre motivo) {
        return switch (motivo) {
            case NORMAL -> CloseStatus.NORMAL;
            case CUOTA_AGOTADA -> CloseStatus.NORMAL.withReason("cuota-agotada");
            case NO_DISPONIBLE -> CloseStatus.SERVICE_OVERLOAD.withReason("no-disponible");
            case ERROR -> CloseStatus.SERVER_ERROR.withReason("error");
        };
    }

    /** Lo que el caso de uso le manda a la app, por el socket. */
    static final class SalidaWebSocket implements SalidaDeVozEnVivo {

        private final WebSocketSession session;

        SalidaWebSocket(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void audio(byte[] pcm16kHz) {
            enviar(new BinaryMessage(pcm16kHz));
        }

        @Override
        public void evento(EventoDeVozEnVivo evento) {
            enviar(new TextMessage(EventoDeVozEnVivoJson.aJson(evento)));
        }

        @Override
        public void cerrar(MotivoDeCierre motivo) {
            try {
                if (session.isOpen()) {
                    session.close(estadoDeCierre(motivo));
                }
            } catch (IOException e) {
                log.debug("No se pudo cerrar el WebSocket de voz en vivo ({})", e.getClass().getSimpleName());
            }
        }

        private void enviar(WebSocketMessage<?> mensaje) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(mensaje);
                }
            } catch (IOException | RuntimeException e) {
                log.debug("No se pudo enviar a la app por la voz en vivo ({})", e.getClass().getSimpleName());
            }
        }
    }

    /** Antes de abrir (o si no se abrio): no hay a quien pasarle nada. */
    private enum SinConversacion implements ConversacionEnVivo {
        INSTANCIA;

        @Override
        public void recibirAudio(byte[] pcm16kHz) {
            // sin conversacion
        }

        @Override
        public void terminar() {
            // sin conversacion
        }
    }
}

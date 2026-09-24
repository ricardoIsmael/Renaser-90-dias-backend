package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort.ConversacionEnVivoNoDisponibleException;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort.SesionEnVivo;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import jakarta.servlet.http.HttpServlet;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.websocket.server.WsSci;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El adaptador contra un <b>Gemini Live falso</b>: un WebSocket de verdad (Tomcat embebido, que ya
 * viene con el backend) que contesta como la API real (D-162). Prueba lo que las funciones puras de
 * {@link MensajesGeminiLive} no alcanzan: el handshake con la key en el header, los frames binarios,
 * la fila de envios, la herramienta de ida y vuelta, el remuestreo en el camino y los cierres.
 */
class GeminiLiveAdapterTest {

    private static final String KEY = "key-de-prueba";

    /** Lo que recibio el servidor falso: los mensajes, y el header de la key como primer elemento. */
    private static final BlockingQueue<String> RECIBIDO = new LinkedBlockingQueue<>();
    /** Que hace el servidor al recibir el setup. */
    private static volatile boolean aceptarSetup = true;

    @TempDir
    Path base;
    private Tomcat tomcat;
    private GeminiLiveAdapter adapter;
    private final OyenteGrabado oyente = new OyenteGrabado();

    @BeforeEach
    void levantarGeminiFalso() throws Exception {
        RECIBIDO.clear();
        aceptarSetup = true;
        tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(0);
        Context contexto = tomcat.addContext("", base.toString());
        Tomcat.addServlet(contexto, "vacio", new HttpServlet() { });
        contexto.addServletMappingDecoded("/", "vacio");
        contexto.addServletContainerInitializer(new WsSci(), null);
        contexto.addServletContainerInitializer((clases, servletContext) -> {
            ServerContainer contenedor = (ServerContainer) servletContext.getAttribute(ServerContainer.class.getName());
            // El setup lleva el prompt entero (~15 KB): con el buffer por defecto de Tomcat (8 KB) el
            // falso lo rechazaba con 1009 (E-234). Gemini real lo acepta.
            contenedor.setDefaultMaxTextMessageBufferSize(1 << 20);
            try {
                contenedor.addEndpoint(ServerEndpointConfig.Builder.create(GeminiFalso.class, "/ws")
                        .configurator(new CapturaDeKey()).build());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, null);
        tomcat.getConnector();
        tomcat.start();
        int puerto = tomcat.getConnector().getLocalPort();
        adapter = new GeminiLiveAdapter(
                new GeminiLiveProperties("ws://localhost:" + puerto + "/ws", "gemini-3.8-live", "Kore", 5_000), KEY);
    }

    @AfterEach
    void apagar() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }

    private static ConversacionEnVivoPort.Apertura apertura() {
        return new ConversacionEnVivoPort.Apertura(new SituacionDelAprendiz(5, 1),
                List.of(DefinicionHerramienta.sinParametros("consultar_habitos_del_dia", "Habitos.")));
    }

    private static String siguiente() throws InterruptedException {
        String valor = RECIBIDO.poll(5, TimeUnit.SECONDS);
        assertThat(valor).as("el servidor falso tenia que recibir algo").isNotNull();
        return valor;
    }

    @Test
    @DisplayName("abre con la key en el header, conversa, ejecuta una herramienta de ida y vuelta y baja el audio a 16 kHz")
    void conversacionCompleta() throws Exception {
        SesionEnVivo sesion = adapter.abrir(apertura(), oyente);

        assertThat(siguiente()).isEqualTo("key:" + KEY);
        String setup = siguiente();
        assertThat(setup).contains("\"setup\"", "models/gemini-3.8-live", "Kore", "consultar_habitos_del_dia",
                "Hoy es su dia 5 de 90");

        sesion.enviarAudio(new byte[]{1, 2, 3, 4});
        assertThat(siguiente()).contains("realtimeInput", "audio/pcm;rate=16000");
        assertThat(oyente.eventos.poll(5, TimeUnit.SECONDS)).isEqualTo("oido: Hola");
        assertThat(oyente.eventos.poll(5, TimeUnit.SECONDS)).isEqualTo("herramienta c-1 consultar_habitos_del_dia");

        sesion.responderHerramienta("c-1", "consultar_habitos_del_dia", ResultadoHerramienta.exito("Meditar"));
        assertThat(siguiente()).contains("toolResponse", "c-1", "Meditar");
        // 600 bytes a 24 kHz = 300 muestras -> 299 filtradas (una de retraso) -> 199 a 16 kHz = 398 bytes.
        assertThat(oyente.eventos.poll(5, TimeUnit.SECONDS)).isEqualTo("audio 398");
        assertThat(oyente.eventos.poll(5, TimeUnit.SECONDS)).isEqualTo("dicho: Tienes Meditar");
        assertThat(oyente.eventos.poll(5, TimeUnit.SECONDS)).isEqualTo("turnoCompleto");

        sesion.cerrar();
        assertThat(siguiente()).isEqualTo("cerrada");
    }

    @Test
    @DisplayName("si Gemini cierra antes de setupComplete, abrir falla con el motivo y no se avisa ningun cierre")
    void rechazaElSetup() {
        aceptarSetup = false;

        assertThatThrownBy(() -> adapter.abrir(apertura(), oyente))
                .isInstanceOf(ConversacionEnVivoNoDisponibleException.class)
                .hasMessageContaining("1008")
                .hasMessageNotContaining(KEY);
        assertThat(oyente.eventos).isEmpty();
    }

    @Test
    @DisplayName("si Gemini corta despues de abrir, el oyente se entera una vez")
    void cortaDespues() throws Exception {
        adapter.abrir(apertura(), oyente);
        siguiente();
        siguiente();

        GeminiFalso.cortarTodo();

        assertThat(oyente.eventos.poll(5, TimeUnit.SECONDS)).startsWith("cerrada: cerrada por Gemini (1001");
        assertThat(oyente.eventos.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("sin servidor, abrir falla con no disponible")
    void sinServidor() throws Exception {
        tomcat.stop();

        assertThatThrownBy(() -> adapter.abrir(apertura(), oyente))
                .isInstanceOf(ConversacionEnVivoNoDisponibleException.class);
    }

    @Test
    @DisplayName("prendida sin key, el backend no arranca y dice por que")
    void sinKeyNoArranca() {
        assertThatThrownBy(() -> new GeminiLiveAdapter(
                new GeminiLiveProperties("ws://localhost:1/ws", "m", "Kore", 1_000), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IA_VOZ_EN_VIVO")
                .hasMessageContaining("GOOGLE_GENAI_API_KEY");
    }

    // ---- el Gemini falso -------------------------------------------------------------------

    public static class CapturaDeKey extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest pedido, HandshakeResponse respuesta) {
            List<String> key = pedido.getHeaders().get("x-goog-api-key");
            RECIBIDO.add("key:" + (key == null ? "" : key.getFirst()));
        }
    }

    /** Contesta como Gemini Live: JSON en frames BINARIOS, como la API real. */
    public static class GeminiFalso extends Endpoint {

        private static final List<Session> ABIERTAS = new java.util.concurrent.CopyOnWriteArrayList<>();

        static void cortarTodo() throws IOException {
            for (Session sesion : ABIERTAS) {
                sesion.close(new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "mantenimiento"));
            }
        }

        @Override
        public void onOpen(Session sesion, EndpointConfig config) {
            ABIERTAS.add(sesion);
            sesion.addMessageHandler(String.class, (MessageHandler.Whole<String>) mensaje -> responder(sesion, mensaje));
        }

        @Override
        public void onClose(Session sesion, CloseReason motivo) {
            ABIERTAS.remove(sesion);
            RECIBIDO.add("cerrada");
        }

        private static void responder(Session sesion, String mensaje) {
            RECIBIDO.add(mensaje);
            try {
                if (mensaje.contains("\"setup\"")) {
                    if (!aceptarSetup) {
                        sesion.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "modelo invalido"));
                        return;
                    }
                    enviar(sesion, "{\"setupComplete\":{}}");
                } else if (mensaje.contains("realtimeInput")) {
                    enviar(sesion, "{\"serverContent\":{\"inputTranscription\":{\"text\":\"Hola\"}}}");
                    enviar(sesion, "{\"toolCall\":{\"functionCalls\":[{\"id\":\"c-1\","
                            + "\"name\":\"consultar_habitos_del_dia\",\"args\":{}}]}}");
                } else if (mensaje.contains("toolResponse")) {
                    String audio = Base64.getEncoder().encodeToString(new byte[600]);
                    enviar(sesion, "{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"data\":\""
                            + audio + "\"}}]},\"outputTranscription\":{\"text\":\"Tienes Meditar\"},"
                            + "\"turnComplete\":true}}");
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void enviar(Session sesion, String json) throws IOException {
            sesion.getBasicRemote().sendBinary(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static final class OyenteGrabado implements ConversacionEnVivoPort.Oyente {
        final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();

        @Override
        public void audio(byte[] pcm16kHz) {
            eventos.add("audio " + pcm16kHz.length);
        }

        @Override
        public void oido(String texto) {
            eventos.add("oido: " + texto);
        }

        @Override
        public void dicho(String texto) {
            eventos.add("dicho: " + texto);
        }

        @Override
        public void interrumpido() {
            eventos.add("interrumpido");
        }

        @Override
        public void turnoCompleto() {
            eventos.add("turnoCompleto");
        }

        @Override
        public void pedidoDeHerramienta(String id, InvocacionHerramienta invocacion) {
            eventos.add("herramienta " + id + " " + invocacion.nombre());
        }

        @Override
        public void cerrada(String motivo) {
            eventos.add("cerrada: " + motivo);
        }
    }
}

package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.ConversacionEnVivo;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.MotivoDeCierre;
import com.renaser.os.rag.application.ports.in.voz.ConversarEnVivoUseCase.SalidaDeVozEnVivo;
import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El WebSocket de voz en vivo de punta a punta sobre el Tomcat real (D-162): el handshake pasa por
 * el filtro de Spring Security y por Spring Session con el header {@code X-Auth-Token}, convive con
 * el STOMP del chat, y los frames viajan como dice el contrato de §5.ter. El caso de uso es un mock:
 * lo que se prueba aca es el transporte (la logica esta en {@code ConversacionEnVivoServiceTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class VozEnVivoWebSocketIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @MockitoBean
    private ConversarEnVivoUseCase conversarEnVivo;

    private final UserId actor = UserId.of(UUID.randomUUID());

    /** Como la deja el login real ({@code SesionWebAdapter}): el id del usuario como nombre. */
    private static <S extends Session> String sesionDe(SessionRepository<S> repositorio, UserId actor) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(actor.value().toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }

    private CompletableFuture<WebSocket> conectar(String token, Cliente cliente) {
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder();
        if (token != null) {
            builder.header("X-Auth-Token", token);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + puerto + "/api/v1/renasia/voz/en-vivo"), cliente);
    }

    private static int estadoDelRechazo(CompletableFuture<WebSocket> intento) {
        try {
            intento.get(10, TimeUnit.SECONDS);
            return 101;
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(WebSocketHandshakeException.class);
            return ((WebSocketHandshakeException) e.getCause()).getResponse().statusCode();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("con sesion: listo, audio en los dos sentidos (tambien un frame de mas de 8 KB), fin y cierre 1013")
    void conversacionPorElSocket() throws Exception {
        String token = sesionDe(sesiones, actor);
        ConversacionGrabada conversacion = new ConversacionGrabada();
        AtomicReference<SalidaDeVozEnVivo> salida = new AtomicReference<>();
        when(conversarEnVivo.puedeConversar(actor)).thenReturn(true);
        when(conversarEnVivo.iniciar(eq(actor), any())).thenAnswer(invocacion -> {
            salida.set(invocacion.getArgument(1));
            salida.get().evento(new EventoDeVozEnVivo.Listo(600));
            return conversacion;
        });
        Cliente cliente = new Cliente();

        WebSocket socket = conectar(token, cliente).get(10, TimeUnit.SECONDS);

        assertThat(cliente.recibido.poll(10, TimeUnit.SECONDS)).isEqualTo("{\"tipo\":\"listo\",\"segundosRestantes\":600}");
        socket.sendBinary(ByteBuffer.wrap(new byte[3_200]), true).get(5, TimeUnit.SECONDS);
        assertThat(conversacion.recibido.poll(5, TimeUnit.SECONDS)).isEqualTo("audio 3200");
        // Mas grande que el buffer de 8 KB de Tomcat: llega partido y el handler lo junta (E-234).
        socket.sendBinary(ByteBuffer.wrap(new byte[20_000]), true).get(5, TimeUnit.SECONDS);
        assertThat(conversacion.recibido.poll(5, TimeUnit.SECONDS)).isEqualTo("audio 20000");

        salida.get().audio(new byte[]{1, 2});
        assertThat(cliente.recibido.poll(5, TimeUnit.SECONDS)).isEqualTo("binario 2");

        socket.sendText("{\"tipo\":\"fin\"}", true).get(5, TimeUnit.SECONDS);
        assertThat(conversacion.recibido.poll(5, TimeUnit.SECONDS)).isEqualTo("terminar");

        salida.get().cerrar(MotivoDeCierre.NO_DISPONIBLE);
        assertThat(cliente.recibido.poll(5, TimeUnit.SECONDS)).isEqualTo("cierre 1013 no-disponible");
    }

    @Test
    @DisplayName("sin sesion el handshake es 403, aunque venga X-Actor-Id")
    void sinSesion() {
        CompletableFuture<WebSocket> intento = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("X-Actor-Id", actor.value().toString())
                .buildAsync(URI.create("ws://localhost:" + puerto + "/api/v1/renasia/voz/en-vivo"), new Cliente());

        assertThat(estadoDelRechazo(intento)).isEqualTo(403);
        verify(conversarEnVivo, never()).iniciar(any(), any());
    }

    @Test
    @DisplayName("con sesion de una cuenta suspendida el handshake es 403")
    void cuentaSuspendida() {
        String token = sesionDe(sesiones, actor);
        when(conversarEnVivo.puedeConversar(actor)).thenReturn(false);

        assertThat(estadoDelRechazo(conectar(token, new Cliente()))).isEqualTo(403);
        verify(conversarEnVivo, never()).iniciar(any(), any());
    }

    @Test
    @DisplayName("un token que no es una sesion es 403")
    void tokenInventado() {
        assertThat(estadoDelRechazo(conectar("no-es-una-sesion", new Cliente()))).isEqualTo(403);
    }

    private static final class Cliente implements WebSocket.Listener {
        final BlockingQueue<String> recibido = new LinkedBlockingQueue<>();
        private final StringBuilder texto = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence datos, boolean ultimo) {
            texto.append(datos);
            if (ultimo) {
                recibido.add(texto.toString());
                texto.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer datos, boolean ultimo) {
            recibido.add("binario " + datos.remaining());
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int codigo, String motivo) {
            recibido.add("cierre " + codigo + " " + motivo);
            return null;
        }
    }

    private static final class ConversacionGrabada implements ConversacionEnVivo {
        final BlockingQueue<String> recibido = new LinkedBlockingQueue<>();

        @Override
        public void recibirAudio(byte[] pcm16kHz) {
            recibido.add("audio " + pcm16kHz.length);
        }

        @Override
        public void terminar() {
            recibido.add("terminar");
        }
    }
}

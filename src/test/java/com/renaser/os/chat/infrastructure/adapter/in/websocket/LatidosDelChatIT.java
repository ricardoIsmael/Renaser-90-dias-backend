package com.renaser.os.chat.infrastructure.adapter.in.websocket;

import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los latidos del canal en vivo (D-202, propuesto en E-331) contra el Tomcat real, con un cliente
 * STOMP escrito a mano igual que el de la app ({@code conexionStomp.ts}): tramas en BINARIO,
 * {@code heart-beat:10000,10000} en el CONNECT y un salto de línea como latido.
 *
 * <p>Antes de D-202 el broker contestaba {@code heart-beat:0,0}, nunca latía y nunca cerraba una
 * conexión muda: las dos pruebas fallan contra ese código.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LatidosDelChatIT {

    private static final String FIN_DE_TRAMA = "\u0000";
    private static final String LATIDO = "\n";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;

    private final List<WebSocket> abiertos = new ArrayList<>();
    private final ScheduledExecutorService latidosDelCliente = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void cerrar() {
        latidosDelCliente.shutdownNow();
        abiertos.forEach(WebSocket::abort);
    }

    @Test
    @DisplayName("el CONNECTED negocia latidos de 10 s en los dos sentidos (antes: heart-beat:0,0)")
    void elConnectedNegociaLosLatidos() throws Exception {
        Cliente cliente = new Cliente();

        conectarStomp(cliente, "10000,10000");

        Map<String, String> connected = cabeceras(cliente.esperarTrama("CONNECTED", 10));
        assertThat(connected).containsEntry("heart-beat", "10000,10000").containsEntry("version", "1.2");
    }

    @Test
    @DisplayName("la conexión muda se cierra; la que late sigue abierta y recibe los latidos del servidor; "
            + "la que no ofreció latidos no se corta")
    void cierraSoloLaConexionMuda() throws Exception {
        Cliente queLate = new Cliente();
        Cliente muda = new Cliente();
        Cliente sinLatidos = new Cliente();
        WebSocket socketQueLate = conectarStomp(queLate, "10000,10000");
        conectarStomp(muda, "10000,10000");
        conectarStomp(sinLatidos, "0,0");
        queLate.esperarTrama("CONNECTED", 10);
        muda.esperarTrama("CONNECTED", 10);
        sinLatidos.esperarTrama("CONNECTED", 10);
        // Como la app: un latido cada 10 s; acá cada 5 para no depender del borde exacto de 30 s.
        latidosDelCliente.scheduleAtFixedRate(() -> enviar(socketQueLate, LATIDO), 5, 5, TimeUnit.SECONDS);

        // El broker da por muerta a la muda tras 3 × 10 s sin leer nada de ella, y la revisa cada 10 s.
        assertThat(muda.cierre.get(50, TimeUnit.SECONDS)).as("código de cierre de la muda").isEqualTo(1002);
        assertThat(muda.tramas).anySatisfy(t -> assertThat(t).startsWith("ERROR").contains("Session closed."));

        assertThat(queLate.cierre).as("la que late sigue abierta").isNotDone();
        assertThat(queLate.latidosRecibidos()).as("latidos del servidor").isPositive();
        assertThat(sinLatidos.cierre).as("sin latidos acordados no se corta").isNotDone();
    }

    // ── Cliente STOMP mínimo ───────────────────────────────────────────────

    /** Abre el socket con una sesión real (como el login) y manda el CONNECT en binario, como la app. */
    private WebSocket conectarStomp(Cliente cliente, String latidos) throws Exception {
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("X-Auth-Token", sesionNueva())
                .buildAsync(URI.create("ws://localhost:" + puerto + "/ws"), cliente)
                .get(10, TimeUnit.SECONDS);
        abiertos.add(socket);
        enviar(socket, "CONNECT\naccept-version:1.2\nhost:renaser\nheart-beat:" + latidos + "\n\n" + FIN_DE_TRAMA);
        return socket;
    }

    private static void enviar(WebSocket socket, String trama) {
        socket.sendBinary(ByteBuffer.wrap(trama.getBytes(StandardCharsets.UTF_8)), true).join();
    }

    /** Como la deja el login real ({@code SesionWebAdapter}): el id del usuario como nombre. */
    private String sesionNueva() {
        return guardarSesion(sesiones, UUID.randomUUID());
    }

    private static <S extends Session> String guardarSesion(SessionRepository<S> repositorio, UUID actor) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(actor.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }

    private static Map<String, String> cabeceras(String trama) {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        String encabezado = trama.split("\n\n", 2)[0];
        for (String linea : encabezado.split("\n")) {
            int separador = linea.indexOf(':');
            if (separador > 0) {
                cabeceras.putIfAbsent(linea.substring(0, separador), linea.substring(separador + 1));
            }
        }
        return cabeceras;
    }

    /** Guarda cada mensaje de texto del servidor entero: una trama STOMP, o un latido suelto. */
    private static final class Cliente implements WebSocket.Listener {
        final BlockingQueue<String> tramas = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> cierre = new CompletableFuture<>();
        private final StringBuilder parcial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence datos, boolean ultimo) {
            parcial.append(datos);
            if (ultimo) {
                tramas.add(parcial.toString());
                parcial.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int codigo, String motivo) {
            cierre.complete(codigo);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            cierre.completeExceptionally(error);
        }

        String esperarTrama(String comando, int segundos) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(segundos);
            for (String trama : tramas) {
                if (trama.startsWith(comando + "\n")) {
                    return trama;
                }
            }
            while (System.nanoTime() < limite) {
                String trama = tramas.poll(200, TimeUnit.MILLISECONDS);
                if (trama != null && trama.startsWith(comando + "\n")) {
                    return trama;
                }
            }
            throw new AssertionError("No llegó " + comando + " en " + segundos + " s; llegó: " + tramas);
        }

        long latidosRecibidos() {
            return tramas.stream().filter(LATIDO::equals).count();
        }
    }
}

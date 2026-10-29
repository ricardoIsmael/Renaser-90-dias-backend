package com.renaser.os.chat.infrastructure.adapter.in.websocket;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Cliente STOMP mínimo, escrito a mano igual que el de la app ({@code conexionStomp.ts}): tramas en
 * BINARIO, latidos negociados en el CONNECT y un salto de línea como latido. Lo comparten
 * {@link LatidosDelChatIT} y {@link LatidosDelChatCortosIT}.
 *
 * <p>Cada conexión usa una sesión HTTP real guardada en Redis (como la deja el login), así que el
 * handshake pasa por {@code ActorHandshakeInterceptor} de verdad. {@link #close()} aborta todos los
 * sockets abiertos y apaga el cliente HTTP.
 */
final class ConexionesStompDePrueba implements AutoCloseable {

    static final String LATIDO = "\n";
    private static final String FIN_DE_TRAMA = "\u0000";

    private final int puerto;
    private final SessionRepository<? extends Session> sesiones;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<WebSocket> abiertos = new CopyOnWriteArrayList<>();

    ConexionesStompDePrueba(int puerto, SessionRepository<? extends Session> sesiones) {
        this.puerto = puerto;
        this.sesiones = sesiones;
    }

    /** Abre el socket con una sesión real (como el login) y manda el CONNECT en binario, como la app. */
    WebSocket conectar(Escucha escucha, String latidos) throws Exception {
        WebSocket socket = http.newWebSocketBuilder()
                .header("X-Auth-Token", sesionNueva())
                .buildAsync(URI.create("ws://localhost:" + puerto + "/ws"), escucha)
                .get(10, TimeUnit.SECONDS);
        abiertos.add(socket);
        enviar(socket, "CONNECT\naccept-version:1.2\nhost:renaser\nheart-beat:" + latidos + "\n\n" + FIN_DE_TRAMA);
        return socket;
    }

    static void enviar(WebSocket socket, String trama) {
        socket.sendBinary(ByteBuffer.wrap(trama.getBytes(StandardCharsets.UTF_8)), true).join();
    }

    @Override
    public void close() {
        abiertos.forEach(WebSocket::abort);
        http.shutdownNow();
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

    static Map<String, String> cabeceras(String trama) {
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

    /**
     * Guarda cada mensaje de texto del servidor entero (una trama STOMP, o un latido suelto) en el
     * orden en que llegan, y el código con que el servidor cerró el socket.
     *
     * <p>El JDK entrega {@code onText} de todo lo anterior antes que {@code onClose}: cuando
     * {@link #cierre} se completa, {@link #tramas} ya está completa.
     */
    static final class Escucha implements WebSocket.Listener {
        final List<String> tramas = new CopyOnWriteArrayList<>();
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

        /**
         * Espera, con tiempo límite, a que llegue una trama del comando dado. No consume nada: las
         * demás tramas siguen en {@link #tramas} para las aserciones.
         */
        String esperarTrama(String comando, int segundos) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(segundos);
            do {
                for (String trama : tramas) {
                    if (trama.startsWith(comando + "\n")) {
                        return trama;
                    }
                }
                Thread.sleep(50);
            } while (System.nanoTime() < limite);
            throw new AssertionError("No llegó " + comando + " en " + segundos + " s; llegó: " + tramas);
        }

        long latidosRecibidos() {
            return tramas.stream().filter(LATIDO::equals).count();
        }

        /** Las tramas {@code ERROR} recibidas (el broker manda una antes de cerrar una sesión muda). */
        List<String> tramasDeError() {
            return tramas.stream().filter(t -> t.startsWith("ERROR\n")).toList();
        }
    }
}

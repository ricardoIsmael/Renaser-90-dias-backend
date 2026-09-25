package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort.SesionEnVivo;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lo que se le manda a Gemini Live durante la sesion (D-162).
 *
 * <p><b>Los envios van en fila.</b> {@link WebSocket} no admite un envio mientras otro esta
 * pendiente ({@code IllegalStateException}), y aca escriben dos hilos: el de la conexion de la app
 * (audio, cada ~100 ms) y el de la recepcion (respuesta de una herramienta). Cada envio se encadena
 * al anterior sin bloquear a ninguno de los dos.
 */
final class SesionGeminiLive implements SesionEnVivo {

    private static final Logger log = LoggerFactory.getLogger(SesionGeminiLive.class);
    private static final Duration ESPERA_DEL_CIERRE = Duration.ofSeconds(3);

    private final WebSocket webSocket;
    private CompletableFuture<?> ultimoEnvio = CompletableFuture.completedFuture(null);
    private boolean cerrada;

    SesionGeminiLive(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    public void enviarAudio(byte[] pcm16kHz) {
        if (pcm16kHz.length > 0) {
            enviar(MensajesGeminiLive.audio(pcm16kHz));
        }
    }

    @Override
    public void responderHerramienta(String id, String nombre, ResultadoHerramienta resultado) {
        enviar(MensajesGeminiLive.respuestaDeHerramienta(id, nombre, resultado));
    }

    @Override
    public synchronized void cerrar() {
        if (cerrada) {
            return;
        }
        cerrada = true;
        // Si Gemini no contesta el cierre, el socket no puede quedar medio abierto: a los pocos
        // segundos se corta igual.
        ultimoEnvio.handle((ok, error) -> null)
                .thenCompose(nada -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "fin"))
                .whenComplete((ok, error) -> CompletableFuture
                        .delayedExecutor(ESPERA_DEL_CIERRE.toMillis(), TimeUnit.MILLISECONDS)
                        .execute(webSocket::abort));
    }

    private synchronized void enviar(String json) {
        if (cerrada || webSocket.isOutputClosed()) {
            return;
        }
        ultimoEnvio = ultimoEnvio
                .handle((ok, error) -> null)
                .thenCompose(nada -> webSocket.sendText(json, true))
                .whenComplete((ok, error) -> {
                    if (error != null) {
                        log.debug("No se pudo enviar a Gemini Live ({})", error.getClass().getSimpleName());
                    }
                });
    }
}

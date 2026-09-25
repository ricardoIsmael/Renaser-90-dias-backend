package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort.Oyente;
import com.renaser.os.rag.infrastructure.adapter.out.ia.MensajesGeminiLive.DelServidor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lo que llega del WebSocket de Gemini Live, traducido al {@link Oyente} del puerto (D-162).
 *
 * <p><b>Gemini manda el JSON en frames binarios</b>, no de texto (visto en la Fase 0): se aceptan
 * los dos. Un mensaje puede venir partido en varios frames; se junta hasta el ultimo.
 *
 * <p>{@code java.net.http} llama a este listener de a un mensaje por vez y solo pide el siguiente
 * cuando se termina de procesar el actual, asi que el {@link Oyente} nunca recibe dos llamadas en
 * paralelo (lo promete el puerto). Una herramienta que tarda frena la recepcion mientras corre, que
 * es justo lo que hay que hacer: el modelo esta esperando esa respuesta.
 *
 * <p>Antes de {@code setupComplete} nada se reenvia, ni siquiera el cierre: si la sesion no llego a
 * abrirse, el que avisa es {@link GeminiLiveAdapter#abrir} con una excepcion, una sola vez.
 */
final class RecepcionGeminiLive implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(RecepcionGeminiLive.class);

    private final Oyente oyente;
    private final RemuestreoDe24a16kHz remuestreo = new RemuestreoDe24a16kHz();
    private final CompletableFuture<Void> setup = new CompletableFuture<>();
    private final AtomicBoolean avisoDeCierre = new AtomicBoolean();
    private final StringBuilder texto = new StringBuilder();
    private final ByteArrayOutputStream binario = new ByteArrayOutputStream();

    RecepcionGeminiLive(Oyente oyente) {
        this.oyente = oyente;
    }

    /** Se completa con {@code setupComplete}; falla si la conexion se cierra antes. */
    CompletableFuture<Void> setup() {
        return setup;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence datos, boolean ultimo) {
        texto.append(datos);
        if (ultimo) {
            String completo = texto.toString();
            texto.setLength(0);
            procesar(completo);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer datos, boolean ultimo) {
        byte[] pedazo = new byte[datos.remaining()];
        datos.get(pedazo);
        binario.writeBytes(pedazo);
        if (ultimo) {
            String completo = binario.toString(StandardCharsets.UTF_8);
            binario.reset();
            procesar(completo);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int codigo, String motivo) {
        terminar("cerrada por Gemini (" + codigo + (motivo == null || motivo.isBlank() ? "" : ": " + motivo) + ")");
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        terminar("error de conexion (" + error.getClass().getSimpleName() + ")");
    }

    private void terminar(String motivo) {
        if (!setup.isDone()) {
            setup.completeExceptionally(new IllegalStateException(motivo));
            return;
        }
        if (avisoDeCierre.compareAndSet(false, true)) {
            oyente.cerrada(motivo);
        }
    }

    /** Un mensaje raro o un fallo del oyente no puede matar la recepcion: se registra y sigue. */
    private void procesar(String json) {
        try {
            for (DelServidor mensaje : MensajesGeminiLive.leer(json)) {
                entregar(mensaje);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo procesar un mensaje de Gemini Live ({})", e.getClass().getSimpleName());
        }
    }

    private void entregar(DelServidor mensaje) {
        switch (mensaje) {
            case DelServidor.SetupCompleto ignorado -> setup.complete(null);
            case DelServidor.Audio audio -> oyente.audio(remuestreo.convertir(audio.pcm24kHz()));
            case DelServidor.Oido oido -> oyente.oido(oido.texto());
            case DelServidor.Dicho dicho -> oyente.dicho(dicho.texto());
            case DelServidor.Interrumpido ignorado -> oyente.interrumpido();
            case DelServidor.TurnoCompleto ignorado -> oyente.turnoCompleto();
            case DelServidor.PedidoDeHerramienta pedido -> oyente.pedidoDeHerramienta(pedido.id(), pedido.invocacion());
            case DelServidor.Adios ignorado -> log.info("Gemini Live aviso que va a cerrar la sesion (goAway)");
        }
    }
}

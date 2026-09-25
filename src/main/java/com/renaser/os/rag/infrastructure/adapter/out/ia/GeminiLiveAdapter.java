package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * La voz en vivo con Gemini Live (D-162): un WebSocket por conversacion contra
 * {@code BidiGenerateContent}, con el modelo {@code gemini-3.8-live} y la voz Kore.
 *
 * <p>Con {@code java.net.http} del JDK, sin dependencias nuevas. La key va en el header
 * {@code x-goog-api-key}, no en la URL: asi no aparece en ningun log de conexion. Nunca se registra
 * ni la key, ni el prompt, ni lo que dice nadie; solo el tipo de un error.
 *
 * <p>Se prende con {@code renaser.ia.voz.en-vivo.activa=true} ({@code IA_VOZ_EN_VIVO}). Prendido y
 * sin key, <b>el backend no arranca</b> y dice por que: un interruptor encendido que no hace nada
 * seria peor que un error claro (mismo criterio que {@code ProveedorDeVozConfig}, E-228).
 *
 * <p>El audio de Gemini sale a 24 kHz y la app lo quiere a 16 kHz: lo convierte
 * {@link RemuestreoDe24a16kHz}, uno por sesion (lleva estado entre pedazos).
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.voz.en-vivo.activa", havingValue = "true")
@EnableConfigurationProperties(GeminiLiveProperties.class)
class GeminiLiveAdapter implements ConversacionEnVivoPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiLiveAdapter.class);

    private final GeminiLiveProperties propiedades;
    private final String apiKey;
    private final PromptDeVozEnVivo prompt = new PromptDeVozEnVivo();
    /** Hilos virtuales: la recepcion puede quedarse esperando una herramienta sin ocupar un hilo real. */
    private final HttpClient http;

    GeminiLiveAdapter(GeminiLiveProperties propiedades, @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("renaser.ia.voz.en-vivo.activa (IA_VOZ_EN_VIVO) esta en true pero falta "
                    + "la key de Gemini (GOOGLE_GENAI_API_KEY). Ponla o apaga la voz en vivo.");
        }
        this.propiedades = propiedades;
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(propiedades.timeoutMs()))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Override
    public boolean disponible() {
        return true;
    }

    @Override
    public SesionEnVivo abrir(Apertura apertura, Oyente oyente) {
        RecepcionGeminiLive recepcion = new RecepcionGeminiLive(oyente);
        WebSocket webSocket = conectar(recepcion);
        try {
            String setup = MensajesGeminiLive.setup(propiedades.modelo(), propiedades.voz(),
                    prompt.para(apertura.situacion(), apertura.memoria()), apertura.herramientas());
            webSocket.sendText(setup, true).get(propiedades.timeoutMs(), TimeUnit.MILLISECONDS);
            recepcion.setup().get(propiedades.timeoutMs(), TimeUnit.MILLISECONDS);
            return new SesionGeminiLive(webSocket);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            webSocket.abort();
            throw new ConversacionEnVivoNoDisponibleException("interrumpido esperando a Gemini Live");
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            webSocket.abort();
            throw new ConversacionEnVivoNoDisponibleException("Gemini Live no acepto la sesion: " + motivo(e));
        }
    }

    private WebSocket conectar(RecepcionGeminiLive recepcion) {
        try {
            return http.newWebSocketBuilder()
                    .header("x-goog-api-key", apiKey)
                    .connectTimeout(Duration.ofMillis(propiedades.timeoutMs()))
                    .buildAsync(URI.create(propiedades.url()), recepcion)
                    .get(propiedades.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversacionEnVivoNoDisponibleException("interrumpido conectando con Gemini Live");
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new ConversacionEnVivoNoDisponibleException("no se pudo conectar con Gemini Live: " + motivo(e));
        }
    }

    /**
     * Solo el tipo y, si es un cierre de Gemini, su codigo y motivo (vienen de
     * {@link RecepcionGeminiLive}, sin datos de la persona). Nunca el mensaje crudo de una excepcion
     * de conexion: puede traer la URL.
     */
    private static String motivo(Exception e) {
        Throwable causa = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (causa instanceof IllegalStateException && causa.getMessage() != null
                && causa.getMessage().startsWith("cerrada por Gemini")) {
            return causa.getMessage();
        }
        log.debug("Detalle del fallo con Gemini Live: {}", causa.getClass().getName());
        return causa.getClass().getSimpleName();
    }
}

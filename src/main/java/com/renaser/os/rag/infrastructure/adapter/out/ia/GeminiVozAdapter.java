package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * La voz del orbe con Gemini TTS (D-159): voz {@code Kore}, modelo {@code gemini-3.8-flash-lite-tts},
 * elegidos por el dueno escuchando muestras. Usa la misma API key que el chat.
 *
 * <p><b>Transmite:</b> pide la Interactions API con {@code stream: true} y reenvia cada pedazo de
 * sonido apenas llega (~1,5 s el primero), en vez de esperar el audio entero (4 a 7 s). Los
 * pedazos llegan como PCM crudo ({@code audio/l16}: 16 bits, mono, 24 kHz) dentro de eventos SSE
 * {@code step.delta}; aca se les antepone una cabecera WAV con el largo en {@code 0xFFFFFFFF}
 * ("desconocido"), porque cuando sale la cabecera todavia no se sabe cuanto va a durar. El silencio
 * de sobra al principio y al final se recorta al vuelo ({@link RecorteDeSilencio}, E-232).
 *
 * <p>Nunca registra el texto ni la key: solo el status o el tipo de la excepcion.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.voz.proveedor", havingValue = "google")
@EnableConfigurationProperties(GeminiVozProperties.class)
class GeminiVozAdapter implements SintetizarVozPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiVozAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final String RUTA = "/v1beta/interactions?alt=sse";
    static final int MUESTRAS_POR_SEGUNDO = 24_000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final GeminiVozProperties propiedades;
    private final String apiKey;

    GeminiVozAdapter(GeminiVozProperties propiedades,
                     @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        this.propiedades = propiedades;
        this.apiKey = apiKey;
    }

    @Override
    public boolean disponible() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public boolean sintetizar(String texto, Consumer<byte[]> destino) {
        if (!disponible()) {
            return false;
        }
        try {
            HttpResponse<Stream<String>> respuesta = http.send(pedido(texto), HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lineas = respuesta.body()) {
                if (respuesta.statusCode() / 100 != 2) {
                    log.warn("Gemini TTS respondio {}; la app usa el TTS del telefono", respuesta.statusCode());
                    return false;
                }
                Transmision transmision = new Transmision(destino);
                lineas.forEach(transmision::procesarLinea);
                return transmision.completa();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Gemini TTS no devolvio audio ({})", e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private HttpRequest pedido(String texto) throws JsonProcessingException {
        return HttpRequest.newBuilder(URI.create(propiedades.url() + RUTA))
                .timeout(Duration.ofMillis(propiedades.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo(texto)), StandardCharsets.UTF_8))
                .build();
    }

    private Map<String, Object> cuerpo(String texto) {
        Map<String, Object> contenido = Map.of("type", "text", "text", texto,
                "annotations", List.of(Map.of("type", "speech_metadata", "style", propiedades.estilo())));
        return Map.of(
                "model", propiedades.modelo(),
                "stream", true,
                "input", List.of(Map.of("type", "user_input", "content", List.of(contenido))),
                "response_format", Map.of("type", "audio"),
                "generation_config", Map.of("speech_config", List.of(Map.of("voice", propiedades.voz()))));
    }

    /** Cabecera WAV (PCM, mono, 16 bits, 24 kHz) con los largos en "desconocido". */
    static byte[] cabeceraWav() {
        ByteBuffer cabecera = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        cabecera.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(0xFFFFFFFF)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1)
                .putInt(MUESTRAS_POR_SEGUNDO).putInt(MUESTRAS_POR_SEGUNDO * 2)
                .putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(0xFFFFFFFF);
        return cabecera.array();
    }

    /** Lee el SSE de Gemini linea por linea y reenvia el sonido. */
    private static final class Transmision {

        private final Consumer<byte[]> destino;
        private final RecorteDeSilencio recorte = new RecorteDeSilencio(MUESTRAS_POR_SEGUNDO);
        private boolean empezo;
        private boolean termino;

        Transmision(Consumer<byte[]> destino) {
            this.destino = destino;
        }

        void procesarLinea(String linea) {
            String datos = linea.startsWith("data:") ? linea.substring(5).strip() : "";
            if (!datos.startsWith("{")) {
                return; // lineas en blanco, comentarios y el "[DONE]" final
            }
            JsonNode evento = leer(datos);
            String tipo = evento.path("event_type").asText();
            if ("step.delta".equals(tipo)) {
                reenviar(evento.path("delta"));
            } else if ("interaction.completed".equals(tipo)) {
                termino = true;
            }
        }

        private void reenviar(JsonNode delta) {
            String sonido = delta.path("data").asText("");
            if (sonido.isEmpty() || !delta.path("mime_type").asText().startsWith("audio/l16")) {
                return;
            }
            entregar(recorte.agregar(Base64.getDecoder().decode(sonido)));
        }

        private void entregar(byte[] pcm) {
            if (pcm.length == 0) {
                return;
            }
            if (!empezo) {
                destino.accept(cabeceraWav());
                empezo = true;
            }
            destino.accept(pcm);
        }

        /** Entrega la cola retenida, ya sin el silencio del final. */
        boolean completa() {
            entregar(recorte.terminar());
            return empezo && termino;
        }

        private static JsonNode leer(String json) {
            try {
                return JSON.readTree(json);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}

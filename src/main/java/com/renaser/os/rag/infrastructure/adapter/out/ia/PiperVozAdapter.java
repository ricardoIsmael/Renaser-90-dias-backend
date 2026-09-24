package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Voz con Piper (voz {@code es_MX-claude-high}), que corre como servicio aparte
 * ({@code infra/piper/}). Contrato verificado contra {@code piper.http_server}:
 * {@code POST {url}/synthesize} con {@code {"text": ..., "length_scale": ..., "noise_scale": ...,
 * "noise_w_scale": ...}} devuelve un WAV (22050 Hz, mono, 16 bits).
 *
 * <p><b>El WAV se valida por su cabecera RIFF, no por el Content-Type:</b> Piper responde el audio
 * con {@code text/html}. Cualquier otra cosa (una pagina de error, un cuerpo vacio) cuenta como
 * fallo.
 *
 * <p><b>Todo fallo es {@code false}</b> (el puerto lo pide): timeout, servicio caido,
 * 5xx o cuerpo que no es WAV. La app cae al TTS del telefono. El WARN nunca lleva el texto — es
 * lo que la persona esta leyendo o lo que el acompanante le responde — ni el throwable, cuyo
 * mensaje puede arrastrar el cuerpo de la respuesta.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.voz.proveedor", havingValue = "piper")
@EnableConfigurationProperties(PiperVozProperties.class)
class PiperVozAdapter implements SintetizarVozPort {

    private static final Logger log = LoggerFactory.getLogger(PiperVozAdapter.class);

    static final String RUTA_SINTESIS = "/synthesize";
    private static final int LARGO_CABECERA_WAV = 12;

    private final RestClient restClient;
    private final PiperVozProperties propiedades;

    @Autowired
    PiperVozAdapter(RestClient.Builder restClientBuilder, PiperVozProperties propiedades) {
        this(restClientBuilder
                .requestFactory(fabricaConTimeout(Duration.ofMillis(propiedades.timeoutMs())))
                .baseUrl(propiedades.url())
                .build(), propiedades);
    }

    /** Para pruebas: un {@link RestClient} ya atado a un servidor simulado. */
    PiperVozAdapter(RestClient restClient, PiperVozProperties propiedades) {
        this.restClient = restClient;
        this.propiedades = propiedades;
    }

    @Override
    public boolean disponible() {
        return true;
    }

    /** Piper no transmite: el WAV sale entero, de una sola vez. */
    @Override
    public boolean sintetizar(String texto, Consumer<byte[]> destino) {
        Optional<byte[]> audio = pedir(texto);
        audio.ifPresent(destino);
        return audio.isPresent();
    }

    private Optional<byte[]> pedir(String texto) {
        try {
            byte[] audio = restClient.post()
                    .uri(RUTA_SINTESIS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo(texto))
                    .retrieve()
                    .body(byte[].class);
            if (!esWav(audio)) {
                log.warn("Piper respondio algo que no es un WAV ({} bytes); se cae al TTS del telefono",
                        audio == null ? 0 : audio.length);
                return Optional.empty();
            }
            return Optional.of(audio);
        } catch (RestClientException e) {
            log.warn("Piper no devolvio audio ({}); se cae al TTS del telefono", describir(e));
            return Optional.empty();
        }
    }

    private Map<String, Object> cuerpo(String texto) {
        return Map.of(
                "text", texto,
                "length_scale", propiedades.lengthScale(),
                "noise_scale", propiedades.noiseScale(),
                "noise_w_scale", propiedades.noiseWScale());
    }

    /** "RIFF" en los bytes 0-3 y "WAVE" en los 8-11: la cabecera minima de un archivo WAV. */
    static boolean esWav(byte[] audio) {
        if (audio == null || audio.length < LARGO_CABECERA_WAV) {
            return false;
        }
        return "RIFF".equals(ascii(audio, 0)) && "WAVE".equals(ascii(audio, 8));
    }

    private static String ascii(byte[] bytes, int desde) {
        return new String(Arrays.copyOfRange(bytes, desde, desde + 4), StandardCharsets.US_ASCII);
    }

    /** Solo el tipo de fallo y el status HTTP: nunca el mensaje, que puede traer el cuerpo. */
    private static String describir(RestClientException e) {
        if (e instanceof RestClientResponseException respuesta) {
            return e.getClass().getSimpleName() + " " + respuesta.getStatusCode().value();
        }
        return e.getClass().getSimpleName();
    }

    private static SimpleClientHttpRequestFactory fabricaConTimeout(Duration timeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        return fabrica;
    }
}

package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contra un servidor SSE falso con la forma real de la Interactions API (verificada el 2026-09-23
 * con {@code gemini-3.8-flash-lite-tts}): {@code step.delta} con PCM en base64, cierre con
 * {@code interaction.completed} y un {@code data: [DONE]} que no es JSON.
 */
class GeminiVozAdapterTest {

    /** Sonido de verdad (muestras de 5000): el silencio se recorta desde E-232. */
    private static final byte[] PCM_1 = sonido(3);
    private static final byte[] PCM_2 = sonido(2);

    private static byte[] sonido(int muestras) {
        ByteBuffer pcm = ByteBuffer.allocate(muestras * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < muestras; i++) {
            pcm.putShort((short) (i % 2 == 0 ? 5000 : -5000));
        }
        return pcm.array();
    }

    private HttpServer servidor;
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private final AtomicReference<String> keyRecibida = new AtomicReference<>();

    @AfterEach
    void apagar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private GeminiVozAdapter adapterContra(int status, String sse, String apiKey) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1beta/interactions", intercambio -> {
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keyRecibida.set(intercambio.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] respuesta = sse.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "text/event-stream");
            intercambio.sendResponseHeaders(status, respuesta.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(respuesta);
            }
        });
        servidor.start();
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
        return new GeminiVozAdapter(new GeminiVozProperties(url, "gemini-3.8-flash-lite-tts", "Kore",
                "fluida y natural", 5000), apiKey);
    }

    private static String delta(byte[] pcm) {
        return "data: {\"index\":0,\"delta\":{\"mime_type\":\"audio/l16\",\"data\":\""
                + Base64.getEncoder().encodeToString(pcm) + "\"},\"event_type\":\"step.delta\"}\n\n";
    }

    private static final String INICIO = """
            data: {"interaction":{"id":"v1_x","status":"in_progress"},"event_type":"interaction.created"}

            data: {"index":0,"step":{"type":"model_output"},"event_type":"step.start"}

            """;
    private static final String FIN = """
            data: {"index":0,"event_type":"step.stop"}

            data: {"interaction":{"id":"v1_x","status":"completed"},"event_type":"interaction.completed"}

            data: [DONE]

            """;

    @Test
    @DisplayName("reenvia una cabecera WAV y despues el PCM de cada step.delta, en orden")
    void transmiteCabeceraYSonido() throws IOException {
        GeminiVozAdapter adapter = adapterContra(200, INICIO + delta(PCM_1) + delta(PCM_2) + FIN, "key-de-prueba");
        ByteArrayOutputStream entregado = new ByteArrayOutputStream();

        boolean completo = adapter.sintetizar("Hola, ¿como vas?", entregado::writeBytes);

        byte[] bytes = entregado.toByteArray();
        assertThat(completo).isTrue();
        assertThat(Arrays.copyOfRange(bytes, 0, 44)).isEqualTo(GeminiVozAdapter.cabeceraWav());
        byte[] esperado = new byte[PCM_1.length + PCM_2.length];
        System.arraycopy(PCM_1, 0, esperado, 0, PCM_1.length);
        System.arraycopy(PCM_2, 0, esperado, PCM_1.length, PCM_2.length);
        assertThat(Arrays.copyOfRange(bytes, 44, bytes.length)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("pide el modelo, la voz, el estilo y el texto con streaming, y manda la key por header")
    void armaElPedido() throws IOException {
        adapterContra(200, INICIO + delta(PCM_1) + FIN, "key-de-prueba").sintetizar("Hola", bytes -> { });

        assertThat(keyRecibida.get()).isEqualTo("key-de-prueba");
        assertThat(cuerpoRecibido.get())
                .contains("\"model\":\"gemini-3.8-flash-lite-tts\"")
                .contains("\"stream\":true")
                .contains("\"voice\":\"Kore\"")
                .contains("\"style\":\"fluida y natural\"")
                .contains("\"text\":\"Hola\"");
    }

    @Test
    @DisplayName("la cabecera WAV declara PCM mono de 16 bits a 24 kHz y largo desconocido")
    void cabeceraWav() {
        ByteBuffer cabecera = ByteBuffer.wrap(GeminiVozAdapter.cabeceraWav()).order(ByteOrder.LITTLE_ENDIAN);

        assertThat(new String(GeminiVozAdapter.cabeceraWav(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(cabecera.getInt(4)).isEqualTo(0xFFFFFFFF);
        assertThat(cabecera.getShort(20)).isEqualTo((short) 1);
        assertThat(cabecera.getShort(22)).isEqualTo((short) 1);
        assertThat(cabecera.getInt(24)).isEqualTo(24_000);
        assertThat(cabecera.getInt(28)).isEqualTo(48_000);
        assertThat(cabecera.getShort(34)).isEqualTo((short) 16);
        assertThat(cabecera.getInt(40)).isEqualTo(0xFFFFFFFF);
    }

    /**
     * Corregido 2026-10-13 (spec 002, D-267). Decia «un error de Gemini (429, 500) es false». Ahora 429, 408 y 5xx son
     * del proveedor y se avisan con {@link ProveedorIaNoDisponibleException}, para que los cuente el circuito de
     * {@code ia.voz}; quien llama sigue sin entregar audio y la app usa la voz del telefono.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {429, 408, 500, 503})
    @DisplayName("un error del proveedor (429, 408, 5xx) lanza ProveedorIaNoDisponibleException y no entrega nada")
    void errorDelProveedorSeAvisa(int status) throws IOException {
        GeminiVozAdapter adapter = adapterContra(status, "{\"error\":{\"code\":" + status + "}}", "key-de-prueba");
        ByteArrayOutputStream entregado = new ByteArrayOutputStream();

        assertThatThrownBy(() -> adapter.sintetizar("Hola", entregado::writeBytes))
                .isInstanceOf(ProveedorIaNoDisponibleException.class);
        assertThat(entregado.size()).isZero();
    }

    @Test
    @DisplayName("un 400 es nuestro, no del proveedor: false, sin excepcion (no apaga la voz de nadie)")
    void errorNuestroEsFalse() throws IOException {
        GeminiVozAdapter adapter = adapterContra(400, "{\"error\":{\"code\":400}}", "key-de-prueba");
        ByteArrayOutputStream entregado = new ByteArrayOutputStream();

        assertThat(adapter.sintetizar("Hola", entregado::writeBytes)).isFalse();
        assertThat(entregado.size()).isZero();
    }

    @Test
    @DisplayName("sin poder conectarse con Gemini tambien es del proveedor")
    void sinConexionSeAvisa() throws IOException {
        GeminiVozAdapter adapter = adapterContra(200, INICIO + delta(PCM_1) + FIN, "key-de-prueba");
        servidor.stop(0);
        servidor = null;

        assertThatThrownBy(() -> adapter.sintetizar("Hola", bytes -> { }))
                .isInstanceOf(ProveedorIaNoDisponibleException.class);
    }

    @Test
    @DisplayName("si el stream se corta antes de interaction.completed, lo entregado queda pero es false")
    void streamCortadoEsFalse() throws IOException {
        GeminiVozAdapter adapter = adapterContra(200, INICIO + delta(PCM_1), "key-de-prueba");
        ByteArrayOutputStream entregado = new ByteArrayOutputStream();

        assertThat(adapter.sintetizar("Hola", entregado::writeBytes)).isFalse();
        assertThat(entregado.size()).isEqualTo(44 + PCM_1.length);
    }

    @Test
    @DisplayName("sin API key no esta disponible y ni llama a Google")
    void sinKeyNoEstaDisponible() throws IOException {
        GeminiVozAdapter adapter = adapterContra(200, INICIO + delta(PCM_1) + FIN, " ");

        assertThat(adapter.disponible()).isFalse();
        assertThat(adapter.sintetizar("Hola", bytes -> { })).isFalse();
        assertThat(cuerpoRecibido.get()).isNull();
    }
}

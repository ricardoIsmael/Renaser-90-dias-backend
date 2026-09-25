package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contrato contra {@code piper.http_server}, con un servidor simulado (mismo patron que
 * {@code GoogleIdentidadAdapterTest}). Lo que importa: el WAV se reconoce por la cabecera RIFF
 * aunque venga como {@code text/html} (asi responde Piper de verdad), y todo fallo es vacio.
 */
class PiperVozAdapterTest {

    private static final String URL = "http://piper.test:5055";
    private static final PiperVozProperties PROPIEDADES =
            new PiperVozProperties(URL, 5000, 1.06, 0.78, 0.95);
    private static final byte[] WAV = {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};

    private MockRestServiceServer servidor;
    private PiperVozAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new PiperVozAdapter(builder.build(), PROPIEDADES);
    }

    /** El WAV entregado, o vacio si el adaptador dijo que no hubo voz. */
    private Optional<byte[]> sintetizar(String texto) {
        ByteArrayOutputStream entregado = new ByteArrayOutputStream();
        boolean completo = adapter.sintetizar(texto, entregado::writeBytes);
        return completo ? Optional.of(entregado.toByteArray()) : Optional.empty();
    }

    @Test
    @DisplayName("manda el texto y los tres parametros de Piper, y devuelve el WAV aunque venga como text/html")
    void devuelveElWavAunqueElContentTypeSeaHtml() {
        servidor.expect(requestTo(URL + PiperVozAdapter.RUTA_SINTESIS))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text").value("Hola, ¿como estas?"))
                .andExpect(jsonPath("$.length_scale").value(1.06))
                .andExpect(jsonPath("$.noise_scale").value(0.78))
                .andExpect(jsonPath("$.noise_w_scale").value(0.95))
                .andRespond(withSuccess(WAV, MediaType.TEXT_HTML));

        Optional<byte[]> audio = sintetizar("Hola, ¿como estas?");

        assertThat(audio).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(WAV));
        servidor.verify();
    }

    @Test
    @DisplayName("un 200 que no es WAV (pagina de error, cuerpo vacio) es vacio")
    void respuestaQueNoEsWavEsVacia() {
        servidor.expect(requestTo(URL + PiperVozAdapter.RUTA_SINTESIS))
                .andRespond(withSuccess("<html>Internal error</html>", MediaType.TEXT_HTML));

        assertThat(sintetizar("Hola")).isEmpty();
    }

    @Test
    @DisplayName("un 500 de Piper es vacio, sin excepcion")
    void errorDelServidorEsVacio() {
        servidor.expect(requestTo(URL + PiperVozAdapter.RUTA_SINTESIS)).andRespond(withServerError());

        assertThat(sintetizar("Hola")).isEmpty();
    }

    @Test
    @DisplayName("un timeout o servicio caido es vacio, sin excepcion")
    void timeoutEsVacio() {
        servidor.expect(requestTo(URL + PiperVozAdapter.RUTA_SINTESIS))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(sintetizar("Hola")).isEmpty();
    }

    @Test
    @DisplayName("Piper siempre esta disponible cuando es el proveedor elegido")
    void estaDisponible() {
        assertThat(adapter.disponible()).isTrue();
    }

    @Test
    @DisplayName("esWav exige RIFF al principio y WAVE en los bytes 8-11")
    void validacionDeLaCabecera() {
        assertThat(PiperVozAdapter.esWav(WAV)).isTrue();
        assertThat(PiperVozAdapter.esWav(null)).isFalse();
        assertThat(PiperVozAdapter.esWav(new byte[0])).isFalse();
        assertThat(PiperVozAdapter.esWav(new byte[] {'R', 'I', 'F', 'F'})).isFalse();
        assertThat(PiperVozAdapter.esWav(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '}))
                .isFalse();
        assertThat(PiperVozAdapter.esWav("<!doctype html>".getBytes())).isFalse();
    }
}

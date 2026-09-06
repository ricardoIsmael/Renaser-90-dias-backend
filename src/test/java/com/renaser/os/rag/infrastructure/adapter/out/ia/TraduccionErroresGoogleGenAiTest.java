package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que el SDK de Google lanza y lo que el resto del sistema tiene que ver. Sin ningun modelo:
 * son las excepciones reales del SDK construidas a mano.
 */
class TraduccionErroresGoogleGenAiTest {

    @Test
    @DisplayName("un 429 de Google es 'cuota agotada': proveedor no disponible, y esperar 60 s")
    void cuotaAgotada() {
        RuntimeException traducida = TraduccionErroresGoogleGenAi.traducir(
                new ClientException(429, "RESOURCE_EXHAUSTED", "Quota exceeded for quota metric"));

        assertThat(traducida).isInstanceOf(ProveedorIaNoDisponibleException.class);
        ProveedorIaNoDisponibleException proveedor = (ProveedorIaNoDisponibleException) traducida;
        assertThat(proveedor.reintentarEn()).isEqualTo(Duration.ofSeconds(60));
        assertThat(proveedor.getMessage()).isEqualTo(TraduccionErroresGoogleGenAi.MENSAJE_CUOTA);
    }

    @Test
    @DisplayName("un 5xx de Google es transitorio: proveedor no disponible, y esperar 10 s")
    void falloDelServidorDeGoogle() {
        RuntimeException traducida = TraduccionErroresGoogleGenAi.traducir(
                new ServerException(503, "UNAVAILABLE", "The model is overloaded"));

        assertThat(traducida).isInstanceOf(ProveedorIaNoDisponibleException.class);
        assertThat(((ProveedorIaNoDisponibleException) traducida).reintentarEn()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("un 400 de Google es un bug NUESTRO en la solicitud: sale tal cual, no disfrazado de caida")
    void solicitudInvalidaNoSeDisfraza() {
        ClientException original = new ClientException(400, "INVALID_ARGUMENT", "contents is required");

        assertThat(TraduccionErroresGoogleGenAi.traducir(original)).isSameAs(original);
    }

    @Test
    @DisplayName("la ApiException se encuentra aunque venga envuelta por Spring AI en otra excepcion")
    void encuentraLaCausaEnvuelta() {
        RuntimeException envuelta = new RuntimeException("fallo el embedding",
                new IllegalStateException(new ClientException(429, "RESOURCE_EXHAUSTED", "quota")));

        assertThat(TraduccionErroresGoogleGenAi.traducir(envuelta)).isInstanceOf(ProveedorIaNoDisponibleException.class);
    }

    @Test
    @DisplayName("un timeout de red hacia Google tambien es 'proveedor no disponible'")
    void timeoutDeRed() {
        RuntimeException envuelta = new RuntimeException(new SocketTimeoutException("Read timed out"));

        assertThat(TraduccionErroresGoogleGenAi.traducir(envuelta)).isInstanceOf(ProveedorIaNoDisponibleException.class);
    }

    @Test
    @DisplayName("el timeout que el SDK envuelve en GenAiIOException tambien es 'proveedor no disponible'")
    void timeoutEnvueltoPorElSdk() {
        RuntimeException delSdk = new GenAiIOException("Failed to execute request",
                new SocketTimeoutException("timeout"));

        assertThat(TraduccionErroresGoogleGenAi.traducir(delSdk)).isInstanceOf(ProveedorIaNoDisponibleException.class);
    }

    @Test
    @DisplayName("cualquier otra excepcion vuelve intacta: traducir no es tragar")
    void loDemasVuelveIgual() {
        IllegalArgumentException original = new IllegalArgumentException("argumento");

        assertThat(TraduccionErroresGoogleGenAi.traducir(original)).isSameAs(original);
    }
}

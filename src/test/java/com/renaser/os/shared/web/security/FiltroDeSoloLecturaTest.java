package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.FixedClock;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El solo lectura de «ver como» en todo el servidor (contrato fase-2.md §5; D-273). Sin Spring. */
class FiltroDeSoloLecturaTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final String SOPORTE = UUID.randomUUID().toString();

    private final FiltroDeSoloLectura filtro = new FiltroDeSoloLectura(FixedClock.at(AHORA));

    private static MockHttpSession deSoloLectura(Instant venceEn) {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionDeSoloLectura.ABIERTA_POR, SOPORTE);
        sesion.setAttribute(SesionDeSoloLectura.VENCE_EN, venceEn.toEpochMilli());
        sesion.setAttribute(SesionDeSoloLectura.ROL, "MENTOR");
        return sesion;
    }

    private static MockHttpServletRequest pedido(String metodo, String ruta, MockHttpSession sesion) {
        MockHttpServletRequest pedido = new MockHttpServletRequest(metodo, ruta);
        if (sesion != null) {
            pedido.setSession(sesion);
        }
        return pedido;
    }

    private record Resultado(MockHttpServletResponse respuesta, HttpServletRequest pasoAlSiguiente) {
    }

    private Resultado filtrar(MockHttpServletRequest pedido) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(pedido, respuesta, cadena);
        return new Resultado(respuesta, (HttpServletRequest) cadena.getRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("toda escritura de una sesión de solo lectura es 403 MODO_SOLO_LECTURA y no llega al controller")
    void escribirEs403(String metodo) throws Exception {
        Resultado resultado = filtrar(pedido(metodo, "/api/v1/habit-tracks/abc/complete",
                deSoloLectura(AHORA.plusSeconds(600))));

        assertThat(resultado.pasoAlSiguiente()).isNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(403);
        assertThat(resultado.respuesta().getContentAsString())
                .contains("\"codigo\":\"MODO_SOLO_LECTURA\"")
                .contains("Estás viendo la app como otro rol: no se puede cambiar nada.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    @DisplayName("leer pasa, y el pedido queda marcado como de solo lectura (las métricas no lo cuentan)")
    void leerPasa(String metodo) throws Exception {
        Resultado resultado = filtrar(pedido(metodo, "/api/v1/home", deSoloLectura(AHORA.plusSeconds(600))));

        assertThat(resultado.pasoAlSiguiente()).isNotNull();
        assertThat(SesionDeSoloLectura.vieneDeUna(resultado.pasoAlSiguiente())).isTrue();
    }

    @Test
    @DisplayName("las dos salidas pasan aunque escriban: DELETE /soporte-ti/ver-como y POST /auth/logout")
    void lasSalidasPasan() throws Exception {
        assertThat(filtrar(pedido("DELETE", "/api/v1/soporte-ti/ver-como", deSoloLectura(AHORA.plusSeconds(600))))
                .pasoAlSiguiente()).isNotNull();
        assertThat(filtrar(pedido("POST", "/api/v1/auth/logout", deSoloLectura(AHORA.plusSeconds(600))))
                .pasoAlSiguiente()).isNotNull();
    }

    @Test
    @DisplayName("POST /users/me pasa: es la lectura del perfil propio que la app pide al entrar")
    void elPerfilPropioPasa() throws Exception {
        assertThat(filtrar(pedido("POST", "/api/v1/users/me", deSoloLectura(AHORA.plusSeconds(600))))
                .pasoAlSiguiente()).isNotNull();
        assertThat(filtrar(pedido("PATCH", "/api/v1/users/me", deSoloLectura(AHORA.plusSeconds(600))))
                .respuesta().getStatus()).as("editarlo, no").isEqualTo(403);
    }

    @Test
    @DisplayName("abrir «ver como» desde una sesión de solo lectura es 403: no abre la consola")
    void noAbreOtroVerComo() throws Exception {
        assertThat(filtrar(pedido("POST", "/api/v1/soporte-ti/ver-como", deSoloLectura(AHORA.plusSeconds(600))))
                .respuesta().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("un WebSocket que no es el del chat (la voz en vivo con SER) es 403; el del chat (/ws) pasa")
    void websockets() throws Exception {
        MockHttpServletRequest voz = pedido("GET", "/api/v1/renasia/voz/en-vivo", deSoloLectura(AHORA.plusSeconds(600)));
        voz.addHeader("Upgrade", "websocket");
        MockHttpServletRequest chat = pedido("GET", "/ws", deSoloLectura(AHORA.plusSeconds(600)));
        chat.addHeader("Upgrade", "WebSocket");

        assertThat(filtrar(voz).respuesta().getStatus()).isEqualTo(403);
        assertThat(filtrar(chat).pasoAlSiguiente()).isNotNull();
    }

    @Test
    @DisplayName("vencida (30 min, aunque se siga usando): 401 VER_COMO_VENCIDO y la sesión se cierra")
    void vencida() throws Exception {
        MockHttpSession sesion = deSoloLectura(AHORA);
        Resultado resultado = filtrar(pedido("GET", "/api/v1/home", sesion));

        assertThat(resultado.pasoAlSiguiente()).isNull();
        assertThat(resultado.respuesta().getStatus()).isEqualTo(401);
        assertThat(resultado.respuesta().getContentAsString()).contains("\"codigo\":\"VER_COMO_VENCIDO\"");
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("sin fecha de vencimiento legible se trata como vencida (falla cerrado)")
    void sinVencimiento() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(SesionDeSoloLectura.ABIERTA_POR, SOPORTE);

        assertThat(filtrar(pedido("GET", "/api/v1/home", sesion)).respuesta().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("una sesión común, o sin sesión, no se toca: escribe como siempre")
    void sesionComun() throws Exception {
        Resultado comun = filtrar(pedido("POST", "/api/v1/wall/posts", new MockHttpSession()));
        Resultado sinSesion = filtrar(pedido("POST", "/api/v1/auth/login", null));

        assertThat(comun.pasoAlSiguiente()).isNotNull();
        assertThat(SesionDeSoloLectura.vieneDeUna(comun.pasoAlSiguiente())).isFalse();
        assertThat(sinSesion.pasoAlSiguiente()).isNotNull();
    }
}

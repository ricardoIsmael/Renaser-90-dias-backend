package com.renaser.os.shared.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** V-7 (D-180): el header {@code Server-Timing} y la ruta que se registra en el log. */
class TiempoDeRespuestaFilterTest {

    private final TiempoDeRespuestaFilter filtro = new TiempoDeRespuestaFilter();

    @Test
    @DisplayName("el header llega aunque el cuerpo ya se haya enviado cuando termina la cadena")
    void elHeaderSeEscribeAntesDeConfirmarLaRespuesta() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/habit-tracks/today");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain escribeYConfirma = (req, res) -> {
            res.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
            res.flushBuffer();
        };

        filtro.doFilter(request, response, escribeYConfirma);

        assertThat(response.isCommitted()).isTrue();
        assertThat(response.getHeader("Server-Timing")).matches("app;dur=\\d+");
    }

    @Test
    @DisplayName("una respuesta sin cuerpo (204) tambien lleva el header")
    void sinCuerpoTambienLlevaElHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/wall/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilter(request, response,
                (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));

        assertThat(response.getHeader("Server-Timing")).startsWith("app;dur=");
    }

    @Test
    @DisplayName("/actuator no se mide: el health check del CD solo haria ruido")
    void actuatorQuedaAfuera() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilter(request, response, (req, res) -> res.getWriter().write("UP"));

        assertThat(response.getHeader("Server-Timing")).isNull();
    }

    @Test
    @DisplayName("la ruta del log es el patron del controller, sin ids ni query string")
    void laRutaEsElPatronDelController() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/users/0b7e3c1a-1111-4222-8333-944455556666");
        request.setQueryString("email=alguien@ejemplo.com");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/users/{id}");

        assertThat(TiempoDeRespuestaFilter.rutaSinDatos(request)).isEqualTo("/api/v1/users/{id}");
    }

    @Test
    @DisplayName("sin controller (401, 404) los UUID de la ruta se tapan")
    void sinControllerSeTapanLosUuid() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/api/v1/mentoring/trainees/0b7e3c1a-1111-4222-8333-944455556666/semaforo");
        request.setQueryString("desde=2026-09-01");

        assertThat(TiempoDeRespuestaFilter.rutaSinDatos(request))
                .isEqualTo("/api/v1/mentoring/trainees/{id}/semaforo");
    }
}

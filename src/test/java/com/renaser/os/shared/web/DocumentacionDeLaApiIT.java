package com.renaser.os.shared.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El documento OpenAPI se genera entero sobre la app real (CLAUDE.MD §8, docs/API_OPENAPI.md):
 * todas las rutas de /api/v1, la sesion por X-Auth-Token, y el actor de la sesion fuera de los
 * parametros. Si un controller nuevo rompe la generacion (un tipo que springdoc no sabe leer),
 * esta prueba es la que lo dice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DocumentacionDeLaApiIT {

    @LocalServerPort
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("/v3/api-docs documenta toda la API con la sesion por X-Auth-Token")
    void documentoCompleto() throws Exception {
        HttpResponse<String> respuesta = get("/v3/api-docs");
        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode doc = new ObjectMapper().readTree(respuesta.body());

        assertThat(doc.path("openapi").asText()).startsWith("3.1");
        JsonNode esquema = doc.path("components").path("securitySchemes").path("sesion");
        assertThat(esquema.path("in").asText()).isEqualTo("header");
        assertThat(esquema.path("name").asText()).isEqualTo("X-Auth-Token");

        JsonNode paths = doc.path("paths");
        List<String> rutas = new ArrayList<>();
        paths.fieldNames().forEachRemaining(rutas::add);
        assertThat(rutas).allMatch(r -> r.startsWith("/api/v1/"));
        assertThat(rutas).contains("/api/v1/auth/login", "/api/v1/home", "/api/v1/habit-tracks/today");
        assertThat(rutas.size()).isGreaterThan(300);

        // Ninguna operacion le exige al cliente el actor: sale de la sesion (@ActorAutenticado).
        // Ojo: un filtro OPCIONAL llamado `actor` existe y es legitimo (la auditoria de Soporte TI
        // filtra por quien hizo el cambio); lo que no puede aparecer es un `actor` obligatorio.
        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> ruta = it.next();
            ruta.getValue().forEach(op -> op.path("parameters").forEach(p -> {
                boolean actorObligatorio = "actor".equals(p.path("name").asText()) && p.path("required").asBoolean();
                assertThat(actorObligatorio).as("actor obligatorio en %s", ruta.getKey()).isFalse();
            }));
        }
    }

    @Test
    @DisplayName("Swagger UI se sirve")
    void swaggerUi() throws Exception {
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }
}

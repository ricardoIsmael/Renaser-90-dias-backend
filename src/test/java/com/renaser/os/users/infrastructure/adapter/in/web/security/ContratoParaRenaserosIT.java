package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-336: genera, desde la app real, el contrato que se entrega a una integración de renaseros y lo deja en
 * {@code target/openapi/renaser-api-renaseros.json} (docs/API_OPENAPI.md, «Pasar el contrato a una integración»). Vigila
 * que no se escape nada del equipo: ninguna ruta de personal, ningún permiso que un TRAINEE no tenga.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ContratoParaRenaserosIT {

    static final Path SALIDA = Path.of("target", "openapi", "renaser-api-renaseros.json");

    @LocalServerPort
    int puerto;

    @Test
    @DisplayName("el contrato para renaseros trae lo de un participante y nada del equipo")
    void generaElContrato() throws Exception {
        HttpResponse<String> respuesta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        ObjectMapper json = new ObjectMapper();
        ObjectNode contrato = ContratoParaRenaseros.filtrar((ObjectNode) json.readTree(respuesta.body()));

        List<String> rutas = new ArrayList<>();
        contrato.path("paths").fieldNames().forEachRemaining(rutas::add);
        assertThat(rutas).contains("/api/v1/auth/login", "/api/v1/home", "/api/v1/habit-tracks/today");
        assertThat(rutas).noneMatch(r -> ContratoParaRenaseros.PREFIJOS_DE_PERSONAL.stream().anyMatch(r::startsWith));
        // Rutas del equipo que declaran USE_APP y dejan el control al servicio: se colaban (revisión del 09/11).
        assertThat(rutas).doesNotContain("/api/v1/semaforo/groups", "/api/v1/calendar/audience/people",
                "/api/v1/calendar/events/{id}/attendance", "/api/v1/me/formacion/activa");
        assertThat(contrato.has("servers")).as("sin el localhost de la prueba").isFalse();
        contrato.path("paths").forEach(item -> item.forEach(operacion -> {
            JsonNode permiso = operacion.path("x-permiso");
            if (!permiso.isMissingNode()) {
                assertThat(permiso.asText()).doesNotStartWith("MANAGE_").isNotIn("OPERATE_PLATFORM", "ADJUST_POINTS",
                        "MODERATE_WALL", "VIEW_MENTOR_CORPS", "APPROVE_ACCOUNT_REQUEST");
            }
        }));

        Files.createDirectories(SALIDA.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(SALIDA.toFile(), contrato);
        assertThat(Files.size(SALIDA)).isPositive();
    }
}

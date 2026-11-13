package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El número de WhatsApp es único en «Solicitar acceso» (spec 051), por HTTP contra Postgres y Redis de verdad: el paso del WhatsApp
 * ({@code POST /auth/whatsapp-code/number}) dice 409 {@code NUMERO_EN_USO} si el número está confirmado en Yo por otra cuenta, declarado en la
 * Ficha de otra persona o en una solicitud pendiente de otra, comparando sin espacios; uno libre pasa; y la consulta tiene tope por origen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class NumeroUnicoEnElRegistroIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUTA = "/api/v1/auth/whatsapp-code/number";

    @DynamicPropertySource
    static void canalConfigurado(DynamicPropertyRegistry registro) {
        registro.add("renaser.whatsapp.url", () -> "http://127.0.0.1:9/v2");
        registro.add("renaser.whatsapp.api-key", () -> "clave-falsa-de-la-prueba");
        registro.add("renaser.whatsapp.numero-emisor", () -> "+51913409458");
        registro.add("renaser.whatsapp.plantilla-codigo", () -> "renaser_codigo_v1");
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;

    private PedidosDePrueba pedidos;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.solicitudes_cuenta WHERE email LIKE '%@numero-unico.test'");
        pedidos.borrarCuentas();
    }

    private HttpResponse<String> comprobar(String numero) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("whatsapp", numero)))).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertEnUso(HttpResponse<String> r) throws Exception {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(409);
        assertThat(JSON.readTree(r.body()).get("codigo").asText()).isEqualTo("NUMERO_EN_USO");
        assertThat(r.body()).doesNotContain("987654321");
    }

    // spec 051 FR-014
    @Test
    @DisplayName("un numero libre pasa con 204")
    void unNumeroLibrePasa() throws Exception {
        assertThat(comprobar("+51 987 654 321").statusCode()).isEqualTo(204);
    }

    // spec 051 FR-014
    @Test
    @DisplayName("un numero confirmado en Yo por otra cuenta es 409 NUMERO_EN_USO, escrito como se escriba")
    void confirmadoEnOtraCuenta() throws Exception {
        UUID otra = pedidos.usuario("MENTOR");
        jdbc.update("INSERT INTO renaser.whatsapp_confirmados (usuario_id, numero) VALUES (?, '+51987654321')", otra);

        assertEnUso(comprobar("+51 987 654 321"));
        assertEnUso(comprobar("+51987654321"));
    }

    // spec 051 FR-014
    @Test
    @DisplayName("un numero declarado en la Ficha de otra persona es 409 NUMERO_EN_USO")
    void declaradoEnLaFichaDeOtra() throws Exception {
        UUID otra = pedidos.usuario("APRENDIZ");
        jdbc.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto)
                SELECT ?, id, '+51 987654321' FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'whatsapp'
                """, otra);

        assertEnUso(comprobar("+51 987 654 321"));
        assertThat(comprobar("+51 911 111 111").statusCode()).isEqualTo(204);
    }

    // spec 051 FR-014
    @Test
    @DisplayName("un numero de una solicitud PENDIENTE es 409; el de una rechazada ya no cuenta")
    void enUnaSolicitudPendiente() throws Exception {
        UUID otra = pedidos.usuario("APRENDIZ");
        jdbc.update("INSERT INTO renaser.solicitudes_cuenta (usuario_id, email, nombre_completo, telefono, estado) "
                + "VALUES (?, 'pendiente@numero-unico.test', 'Pendiente', '+51 987 654 321', 'PENDIENTE')", otra);

        assertEnUso(comprobar("+51987654321"));

        jdbc.update("UPDATE renaser.solicitudes_cuenta SET estado = 'RECHAZADA', motivo_rechazo = 'prueba' WHERE usuario_id = ?", otra);
        assertThat(comprobar("+51987654321").statusCode()).isEqualTo(204);
    }

    // spec 051 FR-014
    @Test
    @DisplayName("lo que no tiene forma de numero es 422 NUMERO_INVALIDO")
    void numeroInvalido() throws Exception {
        HttpResponse<String> r = comprobar("+51 98");

        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(JSON.readTree(r.body()).get("codigo").asText()).isEqualTo("NUMERO_INVALIDO");
    }

    // spec 051 FR-015
    @Test
    @DisplayName("la consulta tiene tope por origen: pasadas 20 por hora, 429")
    void topePorOrigen() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(comprobar("+51 987 654 321").statusCode()).isEqualTo(204);
        }
        assertThat(comprobar("+51 987 654 321").statusCode()).isEqualTo(429);
    }
}

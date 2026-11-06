package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.WhatsappDelRegistro;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El codigo por WhatsApp de punta a punta por HTTP (spec 043), contra Postgres y Redis de verdad y un YCloud FALSO en 127.0.0.1: el interruptor que
 * nace apagado, el alta (el mismo codigo del correo, verificado por {@code /email-verification/confirm}), la recuperacion (solo al numero guardado,
 * verificado por {@code /password/verify-code}) y que la recuperacion responda igual exista o no la cuenta. Nada sale a la red.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CodigoPorWhatsAppIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> RECIBIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer YCLOUD_FALSO = levantarYCloudFalso();

    @DynamicPropertySource
    static void ycloudFalso(DynamicPropertyRegistry registro) {
        registro.add("renaser.whatsapp.url", () -> "http://127.0.0.1:" + YCLOUD_FALSO.getAddress().getPort() + "/v2");
        registro.add("renaser.whatsapp.api-key", () -> "clave-falsa-de-la-prueba");
        registro.add("renaser.whatsapp.numero-emisor", () -> "+51913409458");
        registro.add("renaser.whatsapp.plantilla-codigo", () -> "renaser_codigo_v1");
    }

    private static HttpServer levantarYCloudFalso() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", intercambio -> {
                RECIBIDOS.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = "{\"id\":\"ycloud-1\",\"status\":\"accepted\",\"wamid\":\"wamid.1\"}".getBytes(StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(200, bytes.length);
                intercambio.getResponseBody().write(bytes);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void apagarYCloudFalso() {
        YCLOUD_FALSO.stop(0);
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
    private CopiaDeEstados copiaDeEstados;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private WhatsappDelRegistro whatsappDelRegistro;

    private final HttpClient http = HttpClient.newHttpClient();
    private PedidosDePrueba pedidos;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        RECIBIDOS.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        apagar();
    }

    @AfterEach
    void limpiar() {
        apagar();
        pedidos.borrarCuentas();
    }

    private void apagar() {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'auth.codigo_whatsapp'");
        copiaDeEstados.recargar();
    }

    private void prender() {
        UUID soporte = pedidos.usuario("ADMIN");
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('auth.codigo_whatsapp', true, nextval('renaser.funcionalidades_version_seq'), ?, now())", soporte);
        copiaDeEstados.recargar();
    }

    private HttpResponse<String> pedir(String metodo, String ruta, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo), StandardCharsets.UTF_8));
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json");
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String codigoDe(String cuerpoDeYCloud) throws Exception {
        return JSON.readTree(cuerpoDeYCloud).get("template").get("components").get(0).get("parameters").get(0).get("text").asText();
    }

    private void esperarAlgoEnYCloud() throws InterruptedException {
        for (int i = 0; i < 100 && RECIBIDOS.isEmpty(); i++) {
            Thread.sleep(50);
        }
    }

    // spec 043 FR-001, FR-013, SC-002
    @Test
    @DisplayName("el interruptor NACE apagado: los tres endpoints responden 404 FUNCIONALIDAD_NO_DISPONIBLE y no sale nada")
    void naceApagado() throws Exception {
        for (HttpResponse<String> r : List.of(
                pedir("GET", "/api/v1/auth/whatsapp-code", null),
                pedir("POST", "/api/v1/auth/whatsapp-code/registration", Map.of("email", "nueva@renaser.test", "whatsapp", "+51 987654321")),
                pedir("POST", "/api/v1/auth/whatsapp-code/password", Map.of("email", "nueva@renaser.test")))) {
            assertThat(r.statusCode()).isEqualTo(404);
            assertThat(JSON.readTree(r.body()).get("codigo").asText()).isEqualTo("FUNCIONALIDAD_NO_DISPONIBLE");
        }
        assertThat(RECIBIDOS).isEmpty();
    }

    // spec 043 FR-002
    @Test
    @DisplayName("prendido y configurado, la disponibilidad dice que si")
    void disponible() throws Exception {
        prender();
        HttpResponse<String> r = pedir("GET", "/api/v1/auth/whatsapp-code", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(r.body()).get("available").asBoolean()).isTrue();
    }

    // spec 043 FR-004, FR-005, FR-007, FR-011
    @Test
    @DisplayName("el alta: el WhatsApp lleva el MISMO codigo del correo, se responde enmascarado y /email-verification/confirm lo acepta")
    void altaPorWhatsApp() throws Exception {
        prender();
        String correo = "alta-" + UUID.randomUUID() + "@renaser.test";
        assertThat(pedir("POST", "/api/v1/auth/email-verification/send", Map.of("email", correo)).statusCode()).isEqualTo(202);
        String delCorreo = redis.opsForValue().get("email-verification:codigo:" + correo);

        HttpResponse<String> r = pedir("POST", "/api/v1/auth/whatsapp-code/registration", Map.of("email", correo, "whatsapp", "+51 987654321"));

        assertThat(r.statusCode()).isEqualTo(202);
        assertThat(JSON.readTree(r.body()).get("sentTo").asText()).isEqualTo("+51 9•• ••• •21");
        assertThat(r.body()).doesNotContain("987654321");
        assertThat(RECIBIDOS).hasSize(1);
        JsonNode enviado = JSON.readTree(RECIBIDOS.get(0));
        assertThat(enviado.get("to").asText()).isEqualTo("+51987654321");
        assertThat(enviado.get("template").get("name").asText()).isEqualTo("renaser_codigo_v1");
        assertThat(codigoDe(RECIBIDOS.get(0))).isEqualTo(delCorreo);

        HttpResponse<String> confirmado = pedir("POST", "/api/v1/auth/email-verification/confirm", Map.of("email", correo, "codigo", delCorreo));
        assertThat(confirmado.statusCode()).isEqualTo(200);
    }

    // spec 043 FR-005, FR-010
    @Test
    @DisplayName("el alta: un numero sin forma es 400 y pedir otra vez antes del minuto es 429")
    void altaConErrores() throws Exception {
        prender();
        assertThat(pedir("POST", "/api/v1/auth/whatsapp-code/registration", Map.of("email", "x@renaser.test", "whatsapp", "+51 98")).statusCode())
                .isEqualTo(400);
        assertThat(pedir("POST", "/api/v1/auth/whatsapp-code/registration", Map.of("email", "y@renaser.test", "whatsapp", "+51 987654321"))
                .statusCode()).isEqualTo(202);
        assertThat(pedir("POST", "/api/v1/auth/whatsapp-code/registration", Map.of("email", "y@renaser.test", "whatsapp", "+51 987654321"))
                .statusCode()).isEqualTo(429);
    }

    // spec 043 FR-008, FR-009
    @Test
    @DisplayName("la recuperacion: 202 sin cuerpo; el codigo llega al WhatsApp GUARDADO de la cuenta y /password/verify-code lo acepta")
    void recuperacionPorWhatsApp() throws Exception {
        prender();
        UUID aprendiz = pedidos.usuario("APRENDIZ");
        String correo = aprendiz + "@renaser.test";
        jdbc.update("INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto) "
                + "SELECT ?, id, '+51 912345678' FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'whatsapp'", aprendiz);

        HttpResponse<String> r = pedir("POST", "/api/v1/auth/whatsapp-code/password", Map.of("email", correo));

        assertThat(r.statusCode()).isEqualTo(202);
        assertThat(r.body()).isEmpty();
        esperarAlgoEnYCloud();
        assertThat(RECIBIDOS).hasSize(1);
        assertThat(JSON.readTree(RECIBIDOS.get(0)).get("to").asText()).isEqualTo("+51912345678");
        String codigo = codigoDe(RECIBIDOS.get(0));
        HttpResponse<String> verificado = pedir("POST", "/api/v1/auth/password/verify-code", Map.of("email", correo, "codigo", codigo));
        assertThat(verificado.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(verificado.body()).get("resetToken").asText()).isNotBlank();
    }

    // spec 043 FR-009
    @Test
    @DisplayName("la recuperacion responde EXACTAMENTE igual sin cuenta y con una cuenta sin WhatsApp: 202 sin cuerpo, y no sale nada")
    void recuperacionNoRevelaNada() throws Exception {
        prender();
        UUID sinWhatsapp = pedidos.usuario("APRENDIZ");

        HttpResponse<String> sinCuenta = pedir("POST", "/api/v1/auth/whatsapp-code/password", Map.of("email", "nadie-" + UUID.randomUUID() + "@renaser.test"));
        HttpResponse<String> sinNumero = pedir("POST", "/api/v1/auth/whatsapp-code/password", Map.of("email", sinWhatsapp + "@renaser.test"));

        assertThat(sinCuenta.statusCode()).isEqualTo(202).isEqualTo(sinNumero.statusCode());
        assertThat(sinCuenta.body()).isEmpty();
        assertThat(sinNumero.body()).isEmpty();
        Thread.sleep(300);
        assertThat(RECIBIDOS).isEmpty();
    }

    // spec 043 FR-006
    @Test
    @DisplayName("el WhatsApp del alta queda en la respuesta «whatsapp» de la ficha y no pisa uno que ya exista")
    void elWhatsappDelAltaLlegaALaFicha() {
        UUID persona = pedidos.usuario("APRENDIZ");

        whatsappDelRegistro.llevarALaFicha(UserId.of(persona), "+51 987654321");
        whatsappDelRegistro.llevarALaFicha(UserId.of(persona), "+51 900000000");

        String guardado = jdbc.queryForObject("SELECT r.valor_texto FROM renaser.respuestas_onboarding r "
                + "JOIN renaser.preguntas_onboarding p ON p.id = r.pregunta_id WHERE r.usuario_id = ? AND p.clave_pregunta = 'whatsapp'",
                String.class, persona);
        assertThat(guardado).isEqualTo("+51 987654321");
    }
}

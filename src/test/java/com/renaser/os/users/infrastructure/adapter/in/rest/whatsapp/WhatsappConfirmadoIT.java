package com.renaser.os.users.infrastructure.adapter.in.rest.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
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
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «WhatsApp confirmado en Yo» (spec 051) de punta a punta por HTTP, contra Postgres y Redis de verdad y un YCloud FALSO en 127.0.0.1: pedir el
 * codigo no guarda nada, un codigo equivocado tampoco, confirmar guarda y la lectura lo devuelve enmascarado, un numero ya confirmado en otra
 * cuenta se rechaza, y la recuperacion de la contraseña de una cuenta del personal (sin ficha) llega a ese numero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WhatsappConfirmadoIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> RECIBIDOS = new CopyOnWriteArrayList<>();
    private static final HttpServer YCLOUD_FALSO = levantarYCloudFalso();
    private static final String RUTA = "/api/v1/users/me/whatsapp";

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

    private PedidosDePrueba pedidos;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        RECIBIDOS.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave IN ('cuenta.whatsapp', 'auth.codigo_whatsapp')");
        copiaDeEstados.recargar();
    }

    @AfterEach
    void limpiar() {
        pedidos.borrarCuentas();
    }

    /** La recuperacion es publica, sin sesion. */
    private HttpResponse<String> recuperar(String correo) throws Exception {
        return java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + puerto + "/api/v1/auth/whatsapp-code/password"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email", correo))))
                        .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String codigoEnviado() throws Exception {
        for (int i = 0; i < 100 && RECIBIDOS.isEmpty(); i++) {
            Thread.sleep(50);
        }
        String ultimo = RECIBIDOS.get(RECIBIDOS.size() - 1);
        return JSON.readTree(ultimo).get("template").get("components").get(0).get("parameters").get(0).get("text").asText();
    }

    private String confirmadoEnLaBase(UUID cuenta) {
        return jdbc.query("SELECT numero FROM renaser.whatsapp_confirmados WHERE usuario_id = ?", (rs, i) -> rs.getString(1), cuenta)
                .stream().findFirst().orElse(null);
    }

    // spec 051 FR-001
    @Test
    @DisplayName("la lectura dice que esta disponible y que aun no hay numero confirmado")
    void lecturaSinNumero() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("MENTOR"));

        HttpResponse<String> r = pedidos.enviar("GET", RUTA, sesion, null);

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(pedidos.json(r).get("available").asBoolean()).isTrue();
        assertThat(pedidos.json(r).get("confirmed").isNull()).isTrue();
    }

    // spec 051 FR-002, FR-003, FR-008, FR-009, SC-002
    @Test
    @DisplayName("pedir el codigo NO guarda nada; un codigo equivocado tampoco; el correcto guarda y la lectura lo devuelve enmascarado")
    void confirmarGuardaSoloConElCodigo() throws Exception {
        UUID cuenta = pedidos.usuario("MENTOR");
        String sesion = pedidos.sesionDe(cuenta);

        HttpResponse<String> pedido = pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321"));
        assertThat(pedido.statusCode()).isEqualTo(202);
        assertThat(pedidos.json(pedido).get("sentTo").asText()).isEqualTo("+51 9•• ••• •21");
        assertThat(pedido.body()).doesNotContain("987654321");
        assertThat(RECIBIDOS).hasSize(1);
        assertThat(JSON.readTree(RECIBIDOS.get(0)).get("to").asText()).isEqualTo("+51987654321");
        assertThat(confirmadoEnLaBase(cuenta)).as("pedir el codigo no guarda el numero").isNull();

        String codigo = codigoEnviado();
        String equivocado = codigo.equals("000000") ? "111111" : "000000";
        HttpResponse<String> mal = pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 987654321", "code", equivocado));
        assertThat(mal.statusCode()).isEqualTo(422);
        assertThat(pedidos.json(mal).get("codigo").asText()).isEqualTo("CODIGO_INCORRECTO");
        assertThat(confirmadoEnLaBase(cuenta)).isNull();

        HttpResponse<String> bien = pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 987654321", "code", codigo));
        assertThat(bien.statusCode()).isEqualTo(200);
        assertThat(pedidos.json(bien).get("confirmed").asText()).isEqualTo("+51 9•• ••• •21");
        assertThat(confirmadoEnLaBase(cuenta)).isEqualTo("+51987654321");
        assertThat(pedidos.json(pedidos.enviar("GET", RUTA, sesion, null)).get("confirmed").asText()).isEqualTo("+51 9•• ••• •21");

        HttpResponse<String> repetido = pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 987654321", "code", codigo));
        assertThat(repetido.statusCode()).as("un codigo es de un solo uso").isEqualTo(422);
    }

    // spec 051 FR-003
    @Test
    @DisplayName("el codigo de un numero no confirma otro")
    void elCodigoEsDeSuNumero() throws Exception {
        UUID cuenta = pedidos.usuario("ADMIN");
        String sesion = pedidos.sesionDe(cuenta);
        pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321"));
        String codigo = codigoEnviado();

        HttpResponse<String> otroNumero = pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 900000001", "code", codigo));
        assertThat(otroNumero.statusCode()).isEqualTo(422);
        assertThat(confirmadoEnLaBase(cuenta)).isNull();

        assertThat(pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 987654321", "code", codigo)).statusCode()).isEqualTo(200);
        assertThat(confirmadoEnLaBase(cuenta)).isEqualTo("+51987654321");
    }

    // spec 051 FR-002
    @Test
    @DisplayName("un numero sin forma es 422 NUMERO_INVALIDO y pedir otra vez antes del minuto es 429; no sale nada de mas")
    void numeroInvalidoYEspera() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("GUIA_APRENDIZ"));

        HttpResponse<String> invalido = pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 98"));
        assertThat(invalido.statusCode()).isEqualTo(422);
        assertThat(pedidos.json(invalido).get("codigo").asText()).isEqualTo("NUMERO_INVALIDO");
        assertThat(RECIBIDOS).isEmpty();

        assertThat(pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321")).statusCode()).isEqualTo(202);
        assertThat(pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321")).statusCode()).isEqualTo(429);
        assertThat(RECIBIDOS).hasSize(1);
    }

    // spec 051 FR-007
    @Test
    @DisplayName("un numero ya confirmado en otra cuenta se rechaza al confirmar con 409 NUMERO_EN_USO y no se pisa")
    void numeroEnUso() throws Exception {
        UUID primera = pedidos.usuario("MENTOR");
        String sesionPrimera = pedidos.sesionDe(primera);
        pedidos.enviar("POST", RUTA + "/code", sesionPrimera, Map.of("whatsapp", "+51 987654321"));
        pedidos.enviar("POST", RUTA + "/confirm", sesionPrimera, Map.of("whatsapp", "+51 987654321", "code", codigoEnviado()));
        assertThat(confirmadoEnLaBase(primera)).isEqualTo("+51987654321");

        UUID segunda = pedidos.usuario("ADMIN");
        String sesionSegunda = pedidos.sesionDe(segunda);
        RECIBIDOS.clear();
        assertThat(pedidos.enviar("POST", RUTA + "/code", sesionSegunda, Map.of("whatsapp", "+51 987654321")).statusCode())
                .as("pedir el codigo no delata que el numero tiene cuenta").isEqualTo(202);
        HttpResponse<String> r = pedidos.enviar("POST", RUTA + "/confirm", sesionSegunda,
                Map.of("whatsapp", "+51 987654321", "code", codigoEnviado()));

        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(pedidos.json(r).get("codigo").asText()).isEqualTo("NUMERO_EN_USO");
        assertThat(confirmadoEnLaBase(segunda)).isNull();
        assertThat(confirmadoEnLaBase(primera)).isEqualTo("+51987654321");
    }

    // spec 051 FR-002
    @Test
    @DisplayName("pedir el codigo del numero que la cuenta ya tiene confirmado es 409 YA_CONFIRMADO y no manda nada")
    void yaConfirmado() throws Exception {
        String sesion = pedidos.sesionDe(pedidos.usuario("MENTOR"));
        pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321"));
        pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 987654321", "code", codigoEnviado()));
        RECIBIDOS.clear();

        HttpResponse<String> r = pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321"));

        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(pedidos.json(r).get("codigo").asText()).isEqualTo("YA_CONFIRMADO");
        assertThat(RECIBIDOS).isEmpty();
    }

    // spec 051 FR-009
    @Test
    @DisplayName("con el interruptor cuenta.whatsapp apagado los tres endpoints responden 503 FUNCIONALIDAD_APAGADA y no sale nada")
    void apagado() throws Exception {
        UUID soporte = pedidos.usuario("ADMIN");
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('cuenta.whatsapp', false, nextval('renaser.funcionalidades_version_seq'), ?, now())", soporte);
        copiaDeEstados.recargar();
        String sesion = pedidos.sesionDe(pedidos.usuario("MENTOR"));

        for (HttpResponse<String> r : List.of(
                pedidos.enviar("GET", RUTA, sesion, null),
                pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321")),
                pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 987654321", "code", "123456")))) {
            assertThat(r.statusCode()).isEqualTo(503);
            assertThat(pedidos.json(r).get("codigo").asText()).isEqualTo("FUNCIONALIDAD_APAGADA");
        }
        assertThat(RECIBIDOS).isEmpty();
    }

    // spec 051 FR-005, SC-001
    @Test
    @DisplayName("una cuenta del personal sin ficha confirma su numero y la recuperacion de la contraseña le manda el codigo AHI")
    void recuperacionDelPersonal() throws Exception {
        UUID soporte = pedidos.usuario("ADMIN");
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('auth.codigo_whatsapp', true, nextval('renaser.funcionalidades_version_seq'), ?, now())", soporte);
        copiaDeEstados.recargar();
        UUID mentor = pedidos.usuario("MENTOR");
        String correo = mentor + "@renaser.test";
        String sesion = pedidos.sesionDe(mentor);

        pedidos.enviar("POST", RUTA + "/code", sesion, Map.of("whatsapp", "+51 987654321"));
        pedidos.enviar("POST", RUTA + "/confirm", sesion, Map.of("whatsapp", "+51 987654321", "code", codigoEnviado()));
        RECIBIDOS.clear();

        HttpResponse<String> r = recuperar(correo);

        assertThat(r.statusCode()).isEqualTo(202);
        codigoEnviado();
        assertThat(RECIBIDOS).hasSize(1);
        assertThat(JSON.readTree(RECIBIDOS.get(0)).get("to").asText()).isEqualTo("+51987654321");
    }

    // spec 051 FR-005
    @Test
    @DisplayName("el numero confirmado gana al de la ficha; sin el, sigue valiendo el de la ficha")
    void elConfirmadoGanaAlDeLaFicha() throws Exception {
        UUID soporte = pedidos.usuario("ADMIN");
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('auth.codigo_whatsapp', true, nextval('renaser.funcionalidades_version_seq'), ?, now())", soporte);
        copiaDeEstados.recargar();
        UUID aprendiz = pedidos.usuario("APRENDIZ");
        String correo = aprendiz + "@renaser.test";
        jdbc.update("INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto) "
                + "SELECT ?, id, '+51 912345678' FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'whatsapp'", aprendiz);

        recuperar(correo);
        codigoEnviado();
        assertThat(JSON.readTree(RECIBIDOS.get(0)).get("to").asText()).as("sin confirmado, el de la ficha").isEqualTo("+51912345678");

        jdbc.update("INSERT INTO renaser.whatsapp_confirmados (usuario_id, numero) VALUES (?, '+51955555555')", aprendiz);
        RECIBIDOS.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        recuperar(correo);
        codigoEnviado();
        assertThat(JSON.readTree(RECIBIDOS.get(0)).get("to").asText()).as("con confirmado, el confirmado").isEqualTo("+51955555555");
    }
}

package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.domain.model.whatsapp.FirmaDeYCloud;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los recordatorios de eventos por WhatsApp de punta a punta por HTTP (spec 041): el webhook SIN sesion, con su firma real, y el resumen de Administracion con los
 * filtros de permisos y el interruptor {@code eventos.whatsapp} (que nace apagado). El secreto del webhook es de mentira, puesto solo en esta prueba; el proveedor
 * no esta configurado, asi que nada sale a la red. No es la prueba de que llegue un WhatsApp de verdad: eso es SC-015 y requiere el numero y la plantilla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "renaser.whatsapp.webhook-secreto=" + RecordatoriosPorWhatsAppIT.SECRETO)
@Import(TestcontainersConfiguration.class)
class RecordatoriosPorWhatsAppIT {

    static final String SECRETO = "whsec_de-la-prueba-de-integracion";

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

    private final HttpClient http = HttpClient.newHttpClient();
    private PedidosDePrueba pedidos;
    private UUID aprendiz;
    private UUID evento;
    private UUID entrega;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        aprendiz = pedidos.usuario("APRENDIZ");
        evento = UUID.randomUUID();
        entrega = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.entregas_whatsapp (id, clave, evento_id, usuario_id, aviso_min, estado, wamid, proveedor_id, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, 10, 'ACEPTADO', ?, ?, now(), now())
                """, entrega, UUID.randomUUID(), evento, aprendiz, "wamid." + entrega, "ycloud." + entrega);
        apagarElInterruptor();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.entregas_whatsapp WHERE evento_id = ?", evento);
        apagarElInterruptor();
        pedidos.borrarCuentas();
    }

    private void apagarElInterruptor() {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'eventos.whatsapp'");
        copiaDeEstados.recargar();
    }

    private void prenderElInterruptor(UUID quien) {
        apagarElInterruptor();
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('eventos.whatsapp', true, nextval('renaser.funcionalidades_version_seq'), ?, now())", quien);
        copiaDeEstados.recargar();
    }

    private HttpResponse<String> webhook(String cuerpo, String cabeceraDeFirma) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/webhooks/whatsapp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        if (cabeceraDeFirma != null) {
            pedido.header("YCloud-Signature", cabeceraDeFirma);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String firmado(String cuerpo, Instant t) {
        String segundos = String.valueOf(t.getEpochSecond());
        return "t=" + segundos + ",s=" + FirmaDeYCloud.firmar(SECRETO, segundos, cuerpo.getBytes(StandardCharsets.UTF_8));
    }

    private String estadoDeLaEntrega() {
        return jdbc.queryForObject("SELECT estado FROM renaser.entregas_whatsapp WHERE id = ?", String.class, entrega);
    }

    private String avisoDeEstado(String estado) {
        return "{\"type\":\"whatsapp.message.updated\",\"whatsappMessage\":{\"id\":\"ycloud." + entrega + "\",\"externalId\":\"" + entrega
                + "\",\"status\":\"" + estado + "\",\"wamid\":\"wamid." + entrega + "\"}}";
    }

    // ── El webhook ─────────────────────────────────────────────────────────────

    // spec 041 FR-026, SC-010
    @Test
    @DisplayName("el webhook no pide sesion pero sin firma valida no procesa nada: 401 y el estado no se mueve")
    void sinFirmaNoSeProcesa() throws Exception {
        String cuerpo = avisoDeEstado("delivered");

        assertThat(webhook(cuerpo, null).statusCode()).isEqualTo(401);
        assertThat(webhook(cuerpo, "t=" + Instant.now().getEpochSecond() + ",s=" + "0".repeat(64)).statusCode()).isEqualTo(401);
        assertThat(webhook(cuerpo, firmado(cuerpo, Instant.now().minusSeconds(600))).statusCode()).as("firma de hace 10 minutos").isEqualTo(401);
        assertThat(estadoDeLaEntrega()).isEqualTo("ACEPTADO");
    }

    // spec 041 FR-022, FR-026
    @Test
    @DisplayName("con la firma valida, el aviso de «entregado» y luego «leido» avanza el estado en la base; uno tardio no lo retrocede")
    void elEstadoAvanza() throws Exception {
        String entregado = avisoDeEstado("delivered");
        String leido = avisoDeEstado("read");
        String enviado = avisoDeEstado("sent");

        assertThat(webhook(entregado, firmado(entregado, Instant.now())).statusCode()).isEqualTo(200);
        assertThat(estadoDeLaEntrega()).isEqualTo("ENTREGADO");
        assertThat(webhook(leido, firmado(leido, Instant.now())).statusCode()).isEqualTo(200);
        assertThat(webhook(enviado, firmado(enviado, Instant.now())).statusCode()).isEqualTo(200);
        assertThat(estadoDeLaEntrega()).isEqualTo("LEIDO");
    }

    // spec 041 FR-012, FR-027, SC-011
    @Test
    @DisplayName("tocar «No recibir más» apaga la preferencia de WhatsApp de la persona y deja el aviso de la app como estaba; la segunda vez no cambia nada")
    void laBajaPorElBoton() throws Exception {
        String boton = "{\"type\":\"whatsapp.inbound_message.received\",\"whatsappInboundMessage\":{\"from\":\"+51987654321\",\"to\":\"+51900000000\","
                + "\"type\":\"button\",\"button\":{\"payload\":\"BAJA:" + entrega + "\",\"text\":\"No recibir más\"},\"context\":{\"id\":\"wamid." + entrega + "\"}}}";

        assertThat(webhook(boton, firmado(boton, Instant.now())).statusCode()).isEqualTo(200);
        assertThat(webhook(boton, firmado(boton, Instant.now())).statusCode()).isEqualTo(200);

        Map<String, Object> preferencias = jdbc.queryForMap("SELECT count(*) AS n, bool_and(habilitada) AS todas FROM renaser.preferencias_notificacion "
                + "WHERE usuario_id = ?", aprendiz);
        assertThat(preferencias.get("n")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT habilitada FROM renaser.preferencias_notificacion WHERE usuario_id = ? "
                + "AND tipo = 'RECORDATORIO_EVENTO_WHATSAPP'", Boolean.class, aprendiz)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.preferencias_notificacion WHERE usuario_id = ? AND tipo = 'RECORDATORIO_EVENTO'",
                Integer.class, aprendiz)).isZero();
    }

    // spec 041 FR-027, SC-011
    @Test
    @DisplayName("un «BAJA» suelto, sin responder a un mensaje nuestro, no apaga nada")
    void bajaSueltaNoHaceNada() throws Exception {
        String suelto = "{\"type\":\"whatsapp.inbound_message.received\",\"whatsappInboundMessage\":{\"from\":\"+51987654321\",\"to\":\"+51900000000\","
                + "\"type\":\"text\",\"text\":{\"body\":\"BAJA\"}}}";

        assertThat(webhook(suelto, firmado(suelto, Instant.now())).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.preferencias_notificacion WHERE usuario_id = ?", Integer.class, aprendiz)).isZero();
    }

    // ── Preferencias ───────────────────────────────────────────────────────────

    // spec 041 FR-012, FR-041
    @Test
    @DisplayName("Yo → Notificaciones lista «Recordatorios de eventos por WhatsApp» y viene ENCENDIDO de fabrica; la persona lo puede apagar y volver a encender")
    void laFilaDeYo() throws Exception {
        String sesion = pedidos.sesionDe(aprendiz);

        JsonNode antes = pedidos.json(pedidos.enviar("GET", "/api/v1/notification-preferences", sesion, null));
        JsonNode fila = tipo(antes, "RECORDATORIO_EVENTO_WHATSAPP");
        assertThat(fila).as("el servidor la lista").isNotNull();
        assertThat(fila.get("enabled").asBoolean()).isTrue();

        pedidos.enviar("PATCH", "/api/v1/notification-preferences", sesion, Map.of("preferences",
                java.util.List.of(Map.of("type", "RECORDATORIO_EVENTO_WHATSAPP", "enabled", false))));
        JsonNode apagada = pedidos.json(pedidos.enviar("GET", "/api/v1/notification-preferences", sesion, null));
        assertThat(tipo(apagada, "RECORDATORIO_EVENTO_WHATSAPP").get("enabled").asBoolean()).isFalse();
        assertThat(tipo(apagada, "RECORDATORIO_EVENTO").get("enabled").asBoolean()).as("el aviso de la app no se toca").isTrue();

        pedidos.enviar("PATCH", "/api/v1/notification-preferences", sesion, Map.of("preferences",
                java.util.List.of(Map.of("type", "RECORDATORIO_EVENTO_WHATSAPP", "enabled", true))));
        JsonNode encendida = pedidos.json(pedidos.enviar("GET", "/api/v1/notification-preferences", sesion, null));
        assertThat(tipo(encendida, "RECORDATORIO_EVENTO_WHATSAPP").get("enabled").asBoolean()).isTrue();
    }

    private static JsonNode tipo(JsonNode respuesta, String tipo) {
        for (JsonNode fila : respuesta.get("preferences")) {
            if (tipo.equals(fila.get("type").asText())) {
                return fila;
            }
        }
        return null;
    }

    // ── El resumen de Administración ───────────────────────────────────────────

    // spec 041 FR-010, SC-013
    @Test
    @DisplayName("el interruptor eventos.whatsapp NACE apagado: el resumen responde 404 FUNCIONALIDAD_NO_DISPONIBLE hasta que Soporte TI lo prenda")
    void naceApagado() throws Exception {
        String admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));

        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/calendar/events/" + evento + "/whatsapp", admin, null);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(pedidos.json(respuesta).get("codigo").asText()).isEqualTo("FUNCIONALIDAD_NO_DISPONIBLE");
    }

    // spec 041 FR-029, SC-013
    @Test
    @DisplayName("prendido, Administracion y Alquimista ven los conteos; mentor, guia, lider y aprendiz reciben 403; sin sesion no se ve nada")
    void soloAdministracionVeLosConteos() throws Exception {
        UUID admin = pedidos.usuario("ADMIN");
        prenderElInterruptor(admin);
        jdbc.update("UPDATE renaser.entregas_whatsapp SET estado = 'LEIDO' WHERE id = ?", entrega);
        String ruta = "/api/v1/calendar/events/" + evento + "/whatsapp";

        for (String rol : new String[] {"ADMIN", "ALQUIMISTA"}) {
            HttpResponse<String> respuesta = pedidos.enviar("GET", ruta, pedidos.sesionDe(pedidos.usuario(rol)), null);
            assertThat(respuesta.statusCode()).as(rol).isEqualTo(200);
            JsonNode conteos = pedidos.json(respuesta);
            assertThat(conteos.get("enviados").asInt()).isEqualTo(1);
            assertThat(conteos.get("entregados").asInt()).isEqualTo(1);
            assertThat(conteos.get("leidos").asInt()).isEqualTo(1);
            assertThat(conteos.get("fallaron").asInt()).isZero();
            assertThat(conteos.get("sinNumero").asInt()).isZero();
        }
        for (String rol : new String[] {"MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES", "APRENDIZ"}) {
            assertThat(pedidos.enviar("GET", ruta, pedidos.sesionDe(pedidos.usuario(rol)), null).statusCode()).as(rol).isEqualTo(403);
        }
        assertThat(pedidos.enviar("GET", ruta, pedidos.sesionDe(pedidos.usuario("ADMIN", "SUSPENDIDO")), null).statusCode()).as("suspendido").isEqualTo(403);
        assertThat(http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).as("sin sesion no se ve nada").isIn(401, 403);
    }
}

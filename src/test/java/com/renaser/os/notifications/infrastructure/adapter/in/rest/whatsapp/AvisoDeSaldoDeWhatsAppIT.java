package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El aviso de «WhatsApp sin saldo» de punta a punta por HTTP y contra Postgres real (spec 054): la consulta de {@code entregas_whatsapp}, la ventana de 24 horas, el
 * envío bueno posterior que lo apaga y los permisos. No toca ninguna base de producción ni llama a YCloud.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AvisoDeSaldoDeWhatsAppIT {

    private static final String RUTA = "/api/v1/admin/whatsapp/estado";
    private static final String SIN_SALDO = "The account balance is insufficient.";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private PedidosDePrueba pedidos;
    private String sesionDeAdmin;
    private final UUID evento = UUID.randomUUID();
    private final UUID persona = UUID.randomUUID();

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        sesionDeAdmin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        jdbc.update("DELETE FROM renaser.entregas_whatsapp");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.entregas_whatsapp");
        pedidos.borrarCuentas();
    }

    private void entrega(String estado, String motivo, Instant creadoEn) {
        jdbc.update("""
                INSERT INTO renaser.entregas_whatsapp (id, clave, evento_id, usuario_id, aviso_min, estado, motivo, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, 10, ?, ?, ?, ?)
                """, UUID.randomUUID(), UUID.randomUUID(), evento, persona, estado, motivo, java.sql.Timestamp.from(creadoEn), java.sql.Timestamp.from(creadoEn));
    }

    private JsonNode estado() throws Exception {
        HttpResponse<String> respuesta = pedidos.enviar("GET", RUTA, sesionDeAdmin, null);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return pedidos.json(respuesta);
    }

    // spec 054 FR-001, FR-003, FR-005
    @Test
    @DisplayName("con fallas por saldo en las últimas 24 horas avisa, con cuántas y desde cuándo; solo cuentan los FALLIDO con ese motivo")
    void avisaConLasFallasDeSaldo() throws Exception {
        Instant hace3h = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        entrega("FALLIDO", SIN_SALDO, hace3h);
        entrega("FALLIDO", "THE ACCOUNT BALANCE IS INSUFFICIENT", Instant.now().minus(1, ChronoUnit.HOURS));
        entrega("FALLIDO", "La plantilla no está aprobada.", Instant.now().minus(2, ChronoUnit.HOURS));
        entrega("OMITIDO", "Sin WhatsApp", Instant.now().minus(2, ChronoUnit.HOURS));
        entrega("FALLIDO", SIN_SALDO, Instant.now().minus(30, ChronoUnit.HOURS));

        JsonNode cuerpo = estado();

        assertThat(cuerpo.get("sinSaldo").asBoolean()).isTrue();
        assertThat(cuerpo.get("fallaron").asInt()).isEqualTo(2);
        assertThat(Instant.parse(cuerpo.get("desde").asText())).isEqualTo(hace3h);
        assertThat(cuerpo.toString()).as("sin datos personales").doesNotContain(persona.toString());
    }

    // spec 054 FR-001
    @Test
    @DisplayName("una falla de hace más de 24 horas, un OMITIDO «Sin WhatsApp» o una falla de otro motivo no avisan")
    void sinAviso() throws Exception {
        entrega("FALLIDO", SIN_SALDO, Instant.now().minus(25, ChronoUnit.HOURS));
        entrega("OMITIDO", "Sin WhatsApp", Instant.now().minus(1, ChronoUnit.HOURS));
        entrega("FALLIDO", "La plantilla no está aprobada.", Instant.now().minus(1, ChronoUnit.HOURS));

        JsonNode cuerpo = estado();

        assertThat(cuerpo.get("sinSaldo").asBoolean()).isFalse();
        assertThat(cuerpo.get("fallaron").asInt()).isZero();
        assertThat(cuerpo.get("desde").isNull()).isTrue();
    }

    // spec 054 FR-002
    @Test
    @DisplayName("si después de la última falla salió un envío bien, el aviso desaparece; si el bueno fue antes, sigue")
    void unEnvioBuenoLoApaga() throws Exception {
        entrega("FALLIDO", SIN_SALDO, Instant.now().minus(5, ChronoUnit.HOURS));
        entrega("ENTREGADO", null, Instant.now().minus(6, ChronoUnit.HOURS));
        assertThat(estado().get("sinSaldo").asBoolean()).as("el bueno fue antes de la falla").isTrue();

        entrega("ENVIADO", null, Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(estado().get("sinSaldo").asBoolean()).as("salió uno bien después").isFalse();
    }

    // spec 054 FR-004
    @Test
    @DisplayName("Administración y Alquimista lo ven; mentor, guía, líder y aprendiz reciben 403, una cuenta suspendida también y sin sesión no se ve nada")
    void soloAdministracion() throws Exception {
        for (String rol : new String[] {"ADMIN", "ALQUIMISTA"}) {
            assertThat(pedidos.enviar("GET", RUTA, pedidos.sesionDe(pedidos.usuario(rol)), null).statusCode()).as(rol).isEqualTo(200);
        }
        for (String rol : new String[] {"MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES", "APRENDIZ"}) {
            assertThat(pedidos.enviar("GET", RUTA, pedidos.sesionDe(pedidos.usuario(rol)), null).statusCode()).as(rol).isEqualTo(403);
        }
        assertThat(pedidos.enviar("GET", RUTA, pedidos.sesionDe(pedidos.usuario("ADMIN", "SUSPENDIDO")), null).statusCode()).as("suspendido").isEqualTo(403);
        assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).as("sin sesión").isIn(401, 403);
    }
}

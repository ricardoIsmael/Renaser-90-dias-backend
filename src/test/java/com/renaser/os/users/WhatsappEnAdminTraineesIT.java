package com.renaser.os.users;

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

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El WhatsApp de la Ficha Inicial en el panel de personas de Administración (D-320, issue #78; pedido de RenaserOs), de punta a punta:
 * sesión real, los interceptores, los casos de uso y Postgres. Cubre {@code GET /api/v1/admin/trainees} (cada fila trae
 * {@code whatsapp}, tal como se guardó, o {@code null}, también con filtros y páginas) y {@code GET /api/v1/admin/trainees/{id}}, y que
 * el mentor, el líder de mentores, la guía y el aprendiz reciban 403 sin ver el número ni en el cuerpo del error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WhatsappEnAdminTraineesIT {

    private static final String DE_ANA = "+51 987654321";
    private static final String DE_LUIS = "+34 600111222";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private PedidosDePrueba pedidos;
    private String marca;
    private String admin;
    private UUID ana;
    private UUID luis;
    private UUID sinFicha;
    private UUID mentor;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        marca = "wa" + UUID.randomUUID().toString().substring(0, 8);
        admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        ana = persona("APRENDIZ", "Ana");
        luis = persona("APRENDIZ", "Luis");
        sinFicha = persona("APRENDIZ", "Sin ficha");
        mentor = persona("MENTOR", "Mentora");
        responder(ana, DE_ANA);
        responder(luis, DE_LUIS);
        // El personal que no pasó por la ficha no tiene la respuesta; este mentor tampoco.
    }

    @AfterEach
    void limpiar() {
        pedidos.borrarCuentas();
    }

    private UUID persona(String rol, String nombre) {
        UUID id = pedidos.usuario(rol);
        jdbc.update("UPDATE renaser.usuarios SET nombre_completo = ? WHERE id = ?", marca + " " + nombre, id);
        return id;
    }

    private void responder(UUID persona, String whatsapp) {
        jdbc.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, valor_texto)
                SELECT ?, id, ? FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'whatsapp'
                """, persona, whatsapp);
    }

    private JsonNode lista(String consulta) throws Exception {
        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/admin/trainees?" + consulta, admin, null);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return pedidos.json(respuesta);
    }

    private static Map<String, JsonNode> porId(JsonNode pagina) {
        Map<String, JsonNode> filas = new HashMap<>();
        pagina.get("content").forEach(fila -> filas.put(fila.get("id").asText(), fila));
        return filas;
    }

    @Test
    @DisplayName("el listado trae el whatsapp de cada fila tal como se guardó; null (presente en el JSON) para quien no llenó la ficha y para el personal")
    void elListadoTraeElWhatsapp() throws Exception {
        Map<String, JsonNode> filas = porId(lista("q=" + marca + "&size=50"));

        assertThat(filas).hasSize(4);
        assertThat(filas.get(ana.toString()).get("whatsapp").asText()).isEqualTo(DE_ANA);
        assertThat(filas.get(luis.toString()).get("whatsapp").asText()).isEqualTo(DE_LUIS);
        assertThat(filas.get(sinFicha.toString()).has("whatsapp")).as("el campo existe aunque sea null").isTrue();
        assertThat(filas.get(sinFicha.toString()).get("whatsapp").isNull()).isTrue();
        assertThat(filas.get(mentor.toString()).get("whatsapp").isNull()).isTrue();
    }

    @Test
    @DisplayName("sigue saliendo con los filtros de siempre (role, activeOnly, q) y con la paginación: cada fila con SU número")
    void conFiltrosYPaginas() throws Exception {
        Map<String, JsonNode> soloAprendices = porId(lista("q=" + marca + "&role=TRAINEE&activeOnly=true&size=50"));
        assertThat(soloAprendices).containsOnlyKeys(ana.toString(), luis.toString(), sinFicha.toString());
        assertThat(soloAprendices.get(ana.toString()).get("whatsapp").asText()).isEqualTo(DE_ANA);

        Map<String, JsonNode> busqueda = porId(lista("q=" + marca + "%20Luis"));
        assertThat(busqueda).containsOnlyKeys(luis.toString());
        assertThat(busqueda.get(luis.toString()).get("whatsapp").asText()).isEqualTo(DE_LUIS);

        Map<String, JsonNode> todas = new HashMap<>();
        for (int pagina = 0; pagina < 2; pagina++) {
            JsonNode datos = lista("q=" + marca + "&size=2&page=" + pagina);
            assertThat(datos.get("content")).hasSize(2);
            assertThat(datos.get("total").asInt()).isEqualTo(4);
            todas.putAll(porId(datos));
        }
        assertThat(todas).hasSize(4);
        assertThat(todas.get(ana.toString()).get("whatsapp").asText()).isEqualTo(DE_ANA);
        assertThat(todas.get(luis.toString()).get("whatsapp").asText()).isEqualTo(DE_LUIS);
        assertThat(todas.get(sinFicha.toString()).get("whatsapp").isNull()).isTrue();
    }

    @Test
    @DisplayName("la ficha de una persona (GET /admin/trainees/{id}) trae el mismo whatsapp; null sin ficha")
    void laFichaTraeElWhatsapp() throws Exception {
        JsonNode deAna = pedidos.json(pedidos.enviar("GET", "/api/v1/admin/trainees/" + ana, admin, null));
        JsonNode sinNada = pedidos.json(pedidos.enviar("GET", "/api/v1/admin/trainees/" + sinFicha, admin, null));

        assertThat(deAna.get("whatsapp").asText()).isEqualTo(DE_ANA);
        assertThat(sinNada.has("whatsapp")).isTrue();
        assertThat(sinNada.get("whatsapp").isNull()).isTrue();
    }

    @Test
    @DisplayName("un valor vacío o con solo espacios cuenta como «no lo tiene» (null)")
    void unValorVacioEsNull() throws Exception {
        jdbc.update("UPDATE renaser.respuestas_onboarding SET valor_texto = '   ' WHERE usuario_id = ?", luis);

        Map<String, JsonNode> filas = porId(lista("q=" + marca + "&size=50"));

        assertThat(filas.get(luis.toString()).get("whatsapp").isNull()).isTrue();
        assertThat(filas.get(ana.toString()).get("whatsapp").asText()).isEqualTo(DE_ANA);
    }

    @Test
    @DisplayName("MENTOR, LÍDER DE MENTORES, GUÍA y APRENDIZ reciben 403 en el listado y en la ficha, y el número no aparece ni en el error")
    void losDemasRolesRecibenElCuatrocientosTres() throws Exception {
        for (String rol : new String[] {"MENTOR", "LIDER_MENTORES", "GUIA_APRENDIZ", "APRENDIZ"}) {
            String sesion = pedidos.sesionDe(pedidos.usuario(rol));

            HttpResponse<String> lista = pedidos.enviar("GET", "/api/v1/admin/trainees?q=" + marca, sesion, null);
            HttpResponse<String> ficha = pedidos.enviar("GET", "/api/v1/admin/trainees/" + ana, sesion, null);

            assertThat(lista.statusCode()).as("lista, " + rol).isEqualTo(403);
            assertThat(ficha.statusCode()).as("ficha, " + rol).isEqualTo(403);
            assertThat(lista.body() + ficha.body()).as("cuerpo, " + rol).doesNotContain(DE_ANA).doesNotContain("whatsapp");
        }
    }

    @Test
    @DisplayName("el campo no se agregó a otros endpoints: ni el equipo (/admin/staff) ni la propia persona (/auth/me) lo traen")
    void noSaleEnOtrosEndpoints() throws Exception {
        String suSesion = pedidos.sesionDe(ana);

        HttpResponse<String> equipo = pedidos.enviar("GET", "/api/v1/admin/staff?size=50", admin, null);
        HttpResponse<String> yo = pedidos.enviar("GET", "/api/v1/auth/me", suSesion, null);

        assertThat(equipo.statusCode()).isEqualTo(200);
        assertThat(yo.statusCode()).isEqualTo(200);
        assertThat(equipo.body()).doesNotContain("whatsapp").doesNotContain(DE_ANA);
        assertThat(yo.body()).doesNotContain("whatsapp").doesNotContain(DE_ANA);
    }
}

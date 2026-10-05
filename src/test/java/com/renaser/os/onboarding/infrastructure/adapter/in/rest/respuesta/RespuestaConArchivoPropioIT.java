package com.renaser.os.onboarding.infrastructure.adapter.in.rest.respuesta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-528 de punta a punta sobre el Tomcat real: {@code POST /api/v1/onboarding/answers} con sesión de verdad por
 * {@code X-Auth-Token}, Spring Security delante y Postgres con los archivos guardados como los deja
 * {@code POST /onboarding/media}. Antes del cierre, la respuesta con el {@code mediaId} de Ana salía 200 y quedaba
 * apuntando a su PNG; un id inexistente salía 409 (FK). Ahora los dos salen 404 con el mismo cuerpo y nada se
 * guarda.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RespuestaConArchivoPropioIT {

    private static final String RUTA = "/api/v1/onboarding/answers";
    private static final String RECHAZO = "Ese archivo no existe o no es tuyo";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();

    private UUID ana;
    private UUID luis;
    private long firmaDeAna;
    private long firmaDeLuis;
    private int preguntaFirma;
    private int preguntaNombre;

    @BeforeEach
    void seed() {
        ana = usuario("Ana Pérez");
        luis = usuario("Luis Soto");
        firmaDeAna = firma(ana);
        firmaDeLuis = firma(luis);
        preguntaFirma = pregunta("signature");
        preguntaNombre = pregunta("participant_name");
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra medias_onboarding y respuestas_onboarding.
        usuarios.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("con su propia firma: 200 y la respuesta queda apuntando a SU archivo")
    void conSuPropiaFirmaGuarda() throws Exception {
        HttpResponse<String> respuesta = responder(luis, "{\"questionId\":" + preguntaFirma + ",\"mediaId\":"
                + firmaDeLuis + "}");

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(json.readTree(respuesta.body()).get("mediaId").asLong()).isEqualTo(firmaDeLuis);
        assertThat(mediaGuardada(luis, preguntaFirma)).containsExactly(firmaDeLuis);
    }

    @Test
    @DisplayName("con la firma de Ana: 404 «Ese archivo no existe o no es tuyo» y Luis no queda con ninguna respuesta")
    void conLaFirmaAjenaSeRechaza() throws Exception {
        HttpResponse<String> respuesta = responder(luis, "{\"questionId\":" + preguntaFirma + ",\"mediaId\":"
                + firmaDeAna + "}");

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(404);
        assertThat(mensaje(respuesta)).isEqualTo(RECHAZO);
        assertThat(respuesta.body()).doesNotContain(ana.toString());
        assertThat(mediaGuardada(luis, preguntaFirma)).isEmpty();
    }

    @Test
    @DisplayName("un id que no existe: exactamente la misma respuesta que el ajeno (antes era 409)")
    void conUnIdInexistenteMismaRespuesta() throws Exception {
        long inexistente = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000 FROM renaser.medias_onboarding", Long.class);

        HttpResponse<String> ajeno = responder(luis, "{\"questionId\":" + preguntaFirma + ",\"mediaId\":"
                + firmaDeAna + "}");
        HttpResponse<String> noExiste = responder(luis, "{\"questionId\":" + preguntaFirma + ",\"mediaId\":"
                + inexistente + "}");

        assertThat(noExiste.statusCode()).isEqualTo(ajeno.statusCode()).isEqualTo(404);
        // El cuerpo es {message, timestamp}: fuera de la hora, idéntico.
        assertThat(sinHora(noExiste)).isEqualTo(sinHora(ajeno));
        assertThat(mensaje(noExiste)).isEqualTo(RECHAZO);
        assertThat(mediaGuardada(luis, preguntaFirma)).isEmpty();
    }

    @Test
    @DisplayName("si ya firmó, cambiar su firma por la de Ana da 404 y su respuesta sigue apuntando a SU archivo")
    void cambiarLaFirmaPorLaAjenaNoTocaLaSuya() throws Exception {
        assertThat(responder(luis, "{\"questionId\":" + preguntaFirma + ",\"mediaId\":" + firmaDeLuis + "}")
                .statusCode()).isEqualTo(200);

        HttpResponse<String> respuesta = responder(luis, "{\"questionId\":" + preguntaFirma + ",\"mediaId\":"
                + firmaDeAna + "}");

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(404);
        assertThat(mediaGuardada(luis, preguntaFirma)).containsExactly(firmaDeLuis);
    }

    @Test
    @DisplayName("sin mediaId, igual que antes: una respuesta de texto se guarda con 200")
    void sinArchivoIgualQueAntes() throws Exception {
        HttpResponse<String> respuesta = responder(luis, "{\"questionId\":" + preguntaNombre
                + ",\"textValue\":\"Luis Soto\"}");

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("textValue").asText()).isEqualTo("Luis Soto");
        assertThat(cuerpo.get("mediaId").isNull()).isTrue();
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private HttpResponse<String> responder(UUID quien, String cuerpo) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA))
                .header("X-Auth-Token", sesionDe(quien))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private String mensaje(HttpResponse<String> respuesta) throws Exception {
        return json.readTree(respuesta.body()).get("message").asText();
    }

    private JsonNode sinHora(HttpResponse<String> respuesta) throws Exception {
        ObjectNode cuerpo = (ObjectNode) json.readTree(respuesta.body());
        cuerpo.remove("timestamp");
        return cuerpo;
    }

    /** Lo que quedó en la base: el {@code media_id} de la respuesta de esa persona a esa pregunta, si la hay. */
    private List<Long> mediaGuardada(UUID quien, int pregunta) {
        return jdbcTemplate.queryForList("""
                SELECT media_id FROM renaser.respuestas_onboarding WHERE usuario_id = ? AND pregunta_id = ?
                """, Long.class, quien, pregunta);
    }

    /** Como la deja el login real ({@code SesionWebAdapter}): el id del usuario como nombre. */
    private String sesionDe(UUID usuario) {
        return guardarSesion(sesiones, usuario);
    }

    private static <S extends Session> String guardarSesion(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }

    private UUID usuario(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, 'APRENDIZ', 'ACTIVO')
                """, id, id + "@renaser.test", nombre);
        usuarios.add(id);
        return id;
    }

    /** Como la registra {@code POST /onboarding/media} desde {@code guardarFirma} de la app. */
    private long firma(UUID dueno) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO renaser.medias_onboarding (usuario_id, flujo, clave_pregunta, clase, bucket, ruta_storage, mime)
                VALUES (?, 'pacto', 'signature', 'firma', 'onboarding-media', ?, 'image/png')
                RETURNING id
                """, Long.class, dueno, "onboarding/" + dueno + "/firma/" + UUID.randomUUID());
    }

    private int pregunta(String clave) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?", Integer.class, clave);
    }
}

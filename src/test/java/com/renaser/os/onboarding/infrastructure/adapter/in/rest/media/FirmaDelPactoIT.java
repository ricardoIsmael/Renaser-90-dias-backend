package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-253 (2026-10-05) de punta a punta sobre el Tomcat real: sesión de verdad por {@code X-Auth-Token},
 * Spring Security delante y Postgres con la firma guardada como la guarda la app (fila en
 * {@code medias_onboarding} + respuesta a la pregunta {@code signature} con su {@code media_id}).
 *
 * <p>El almacenamiento de las pruebas es el de marcador ({@code renaser.storage.proveedor=noop}): la URL sale
 * como {@code about:blank#pendiente-s3/<ruta>}. Alcanza para comprobar QUÉ objeto se firmó; la firma real de
 * S3 es del SDK (se probó a mano contra un S3 local, ver D-253).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FirmaDelPactoIT {

    private static final String RUTA = "/api/v1/onboarding/pact/signature";

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
    private UUID kelin;
    private String rutaDeLaFirmaDeAna;
    private long firmaDeAna;

    @BeforeEach
    void seed() {
        ana = usuario("APRENDIZ", "Ana Pérez");
        luis = usuario("APRENDIZ", "Luis Soto");
        kelin = usuario("ADMIN", "Kelin Rojas");
        rutaDeLaFirmaDeAna = "onboarding/" + ana + "/firma/" + UUID.randomUUID();
        firmaDeAna = media(ana, "firma", rutaDeLaFirmaDeAna);
        responderLaFirmaDelPacto(ana, firmaDeAna);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra medias_onboarding y respuestas_onboarding.
        usuarios.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("la dueña recibe la URL firmada de SU firma, válida 15 minutos; solo url y expiresAt")
    void laDuenaVeSuFirma() throws Exception {
        Instant antes = Instant.now();
        HttpResponse<String> respuesta = pedir(sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("url").asText()).startsWith("about:blank#pendiente-s3/").endsWith(rutaDeLaFirmaDeAna);
        assertThat(Instant.parse(cuerpo.get("expiresAt").asText()))
                .isBetween(antes.plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
        Set<String> campos = new TreeSet<>();
        cuerpo.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).as("ni bucket, ni ruta, ni mediaId como campos propios").containsExactly("expiresAt", "url");
    }

    @Test
    @DisplayName("sin firma guardada: 404 con un mensaje claro")
    void sinFirmaEs404() throws Exception {
        HttpResponse<String> respuesta = pedir(sesionDe(luis));

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(json.readTree(respuesta.body()).get("message").asText())
                .isEqualTo("Todavía no hay una firma del Pacto guardada");
    }

    @Test
    @DisplayName("otra persona: si su respuesta apunta al archivo de Ana (E-528), 403 y nada de Ana en la respuesta")
    void otraPersonaNoVeLaFirmaAjena() throws Exception {
        // Así quedaba la base si Luis mandaba POST /onboarding/answers con el mediaId de Ana antes del cierre de
        // E-528 (hoy ese POST da 404, RespuestaConArchivoPropioIT); una fila vieja así puede seguir en la base.
        responderLaFirmaDelPacto(luis, firmaDeAna);

        HttpResponse<String> respuesta = pedir(sesionDe(luis));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.body()).doesNotContain(rutaDeLaFirmaDeAna).doesNotContain(ana.toString());
    }

    @Test
    @DisplayName("no hay forma de nombrar a otra persona: un admin que agrega ?userId=<Ana> recibe 404 (la suya)")
    void elParametroDeUsuarioNoExiste() throws Exception {
        HttpResponse<String> respuesta = pedir(RUTA + "?userId=" + ana + "&usuarioId=" + ana, sesionDe(kelin));

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).doesNotContain(rutaDeLaFirmaDeAna);
    }

    @Test
    @DisplayName("autorización negativa: sin sesión y con la cuenta suspendida, 403 y ninguna URL")
    void sinSesionYSuspendidaSon403() throws Exception {
        String sesionDeAna = sesionDe(ana);
        HttpResponse<String> sinSesion = pedir(RUTA, null);
        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", ana);
        HttpResponse<String> suspendida = pedir(sesionDeAna);

        assertThat(sinSesion.statusCode()).isEqualTo(403);
        assertThat(suspendida.statusCode()).isEqualTo(403);
        assertThat(sinSesion.body() + suspendida.body()).doesNotContain(rutaDeLaFirmaDeAna);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private HttpResponse<String> pedir(String sesion) throws Exception {
        return pedir(RUTA, sesion);
    }

    private HttpResponse<String> pedir(String ruta, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.GET().build(), HttpResponse.BodyHandlers.ofString());
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

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        usuarios.add(id);
        return id;
    }

    /** Como la registra {@code POST /onboarding/media} desde {@code guardarFirma} de la app. */
    private long media(UUID dueno, String clase, String ruta) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO renaser.medias_onboarding (usuario_id, flujo, clave_pregunta, clase, bucket, ruta_storage, mime)
                VALUES (?, 'pacto', 'signature', ?, 'onboarding-media', ?, 'image/png')
                RETURNING id
                """, Long.class, dueno, clase, ruta);
    }

    /** La respuesta a la pregunta FIRMA del Pacto (V10), upsert por (usuario, pregunta) como el adaptador real. */
    private void responderLaFirmaDelPacto(UUID usuario, long mediaId) {
        jdbcTemplate.update("""
                INSERT INTO renaser.respuestas_onboarding (usuario_id, pregunta_id, media_id)
                SELECT ?, id, ? FROM renaser.preguntas_onboarding WHERE clave_pregunta = 'signature'
                ON CONFLICT (usuario_id, pregunta_id) DO UPDATE SET media_id = EXCLUDED.media_id
                """, usuario, mediaId);
    }
}

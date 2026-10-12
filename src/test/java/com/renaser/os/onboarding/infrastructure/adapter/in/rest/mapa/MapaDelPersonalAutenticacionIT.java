package com.renaser.os.onboarding.infrastructure.adapter.in.rest.mapa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisión del dueño (2026-10-10): MENTOR, ADMIN y ALCHEMIST guardan su propio Mapa del Poder
 * y lo activan después de elegir su Día 1, igual que el aprendiz. Ejercita el login real,
 * la sesión Redis, los permisos y la persistencia en Postgres; ningún actor se autentica
 * mediante X-Actor-Id. El almacenamiento es el adaptador NoOp de la suite: se comprueban
 * la autorización de la URL de subida y el registro del archivo, no una transferencia a S3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MapaDelPersonalAutenticacionIT {

    private static final String MAPA = "/api/v1/mapa-renacimiento";
    private static final String ACTIVAR = "/api/v1/onboarding/activate-program";
    private static final String MEDIA = "/api/v1/onboarding/media";
    private static final String RESPUESTAS = "/api/v1/onboarding/answers";
    private static final String CLAVE = "ClaveDelMapaIT-2026!";

    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private SessionRepository<? extends Session> sesiones;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private UUID actorDelHeader;

    @AfterEach
    void limpiar() {
        tokens.forEach(sesiones::deleteById);
        usuarios.reversed().forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @ParameterizedTest(name = "{0}: login, Día 1 y mapa propio con objetivos, hábitos y archivo")
    @CsvSource({"MENTOR, MENTOR", "ADMIN, ADMIN", "ALQUIMISTA, ALCHEMIST"})
    void elPersonalGuardaYActivaSuMapaTrasElegirDiaUno(String rolBase, String rolApi) throws Exception {
        UUID persona = usuario(rolBase, "ACTIVO");
        String token = iniciarSesion(persona, rolApi);
        actorDelHeader = usuario("ADMIN", "ACTIVO");
        assertThat(leer(pedir("GET", "/api/v1/auth/me", token, null), 200).get("id").asText())
                .isEqualTo(persona.toString());
        assertThat(pedir("PUT", "/api/v1/rocks/master/CUERPO", token, objetivo()).statusCode())
                .as("sin elegir Día 1 todavía no tiene programa personal").isEqualTo(404);

        JsonNode fechas = leer(pedir("GET", ACTIVAR, token, null), 200);
        String inicio = fechas.get("validStartDates").get(0).asText();
        leer(pedir("POST", ACTIVAR, token, Map.of("startDate", inicio)), 200);
        assertThat(jdbc.queryForObject("""
                SELECT fecha_inicio::text FROM renaser.participantes_programa
                WHERE usuario_id = ? AND programa_activado_en IS NOT NULL AND dias_ajuste_programa = 0
                """, String.class, persona)).isEqualTo(inicio);

        for (String eje : List.of("CUERPO", "TRABAJO", "RELACIONES")) {
            leer(pedir("PUT", "/api/v1/rocks/master/" + eje, token, objetivo()), 200);
        }
        JsonNode habito = leer(pedir("POST", "/api/v1/habits", token, habito()), 201);
        assertThat(habito.get("id").asText()).isNotBlank();
        guardarRespuesta(token, "map_priority_area", "salud");
        guardarRespuesta(token, "map_health_goal_text", "Caminar cuarenta minutos tres veces por semana");
        assertThat(pedir("PUT", MAPA + "/acciones", token, acciones()).statusCode()).isEqualTo(204);
        assertThat(pedir("PUT", MAPA + "/reemplazos", token, reemplazos()).statusCode()).isEqualTo(204);
        JsonNode archivo = registrarArchivo(persona, token);
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM renaser.medias_onboarding WHERE id = ?",
                UUID.class, archivo.get("id").asLong())).isEqualTo(persona);
        assertThat(pedir("POST", MAPA + "/completar", token, null).statusCode()).isEqualTo(204);

        JsonNode mapa = leer(pedir("GET", MAPA, token, null), 200);
        assertThat(mapa.get("stageCompleted").asBoolean()).isTrue();
        assertThat(mapa.get("actions")).hasSize(1);
        assertThat(mapa.get("actions").get(0).get("text").asText()).isEqualTo("Caminar cuarenta minutos");
        assertThat(mapa.get("protocols")).hasSize(1);
        assertThat(leer(pedir("GET", RESPUESTAS + "?flow=mapa_dia7", token, null), 200).toString())
                .contains("Caminar cuarenta minutos tres veces por semana");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.rocas_maestras WHERE participante_id = ?",
                Integer.class, persona)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.acciones_mapa WHERE usuario_id = ?",
                Integer.class, actorDelHeader)).as("el header ajeno nunca cambia al dueño del mapa").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.participantes_programa WHERE usuario_id = ?",
                Integer.class, actorDelHeader)).isZero();
    }

    @ParameterizedTest(name = "{0} suspendido con una sesión todavía viva: 403 en todo el mapa")
    @ValueSource(strings = {"MENTOR", "ADMIN", "ALQUIMISTA"})
    void suspenderLaCuentaCortaElMapaAunqueLaSesionSigaViva(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");
        String token = iniciarSesion(persona, rol.equals("ALQUIMISTA") ? "ALCHEMIST" : rol);
        leer(pedir("GET", MAPA, token, null), 200);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", persona);

        for (Pedido pedido : operaciones(persona)) {
            assertThat(pedir(pedido.metodo(), pedido.ruta(), token, pedido.cuerpo()).statusCode())
                    .as("%s %s", pedido.metodo(), pedido.ruta()).isEqualTo(403);
        }
        assertSinDatosDelMapa(persona);
    }

    @ParameterizedTest(name = "{0} sin aprobar o suspendido: el login no entrega sesión")
    @ValueSource(strings = {"MENTOR", "ADMIN", "ALQUIMISTA"})
    void elRolNoSustituyeLaAprobacionNiLaCuentaActiva(String rol) throws Exception {
        for (String estado : List.of("INACTIVO", "SUSPENDIDO")) {
            UUID persona = usuario(rol, estado);
            HttpResponse<String> respuesta = login(persona);
            assertThat(respuesta.statusCode()).as("%s %s: %s", rol, estado, respuesta.body()).isEqualTo(401);
            assertThat(respuesta.headers().firstValue("X-Auth-Token")).isEmpty();
            assertSinDatosDelMapa(persona);
        }
    }

    @Test
    void unHeaderDeAdministradorSinSesionNoAutorizaElMapa() throws Exception {
        actorDelHeader = usuario("ADMIN", "ACTIVO");
        for (Pedido pedido : operaciones(actorDelHeader)) {
            assertThat(pedir(pedido.metodo(), pedido.ruta(), null, pedido.cuerpo()).statusCode())
                    .as("%s %s sin sesión", pedido.metodo(), pedido.ruta()).isEqualTo(403);
        }
        assertSinDatosDelMapa(actorDelHeader);
    }

    private JsonNode registrarArchivo(UUID persona, String token) throws Exception {
        JsonNode subida = leer(pedir("POST", MEDIA + "/upload-url", token,
                Map.of("flow", "mapa_dia7", "kind", "DOCUMENTO", "contentType", "application/pdf")), 200);
        assertThat(subida.get("path").asText()).startsWith("onboarding/" + persona + "/documento/");
        return leer(pedir("POST", MEDIA, token, Map.of("flow", "mapa_dia7", "kind", "DOCUMENTO",
                "bucket", subida.get("bucket").asText(), "path", subida.get("path").asText(),
                "mime", "application/pdf", "sizeBytes", 128)), 201);
    }

    private void guardarRespuesta(String token, String clave, String valor) throws Exception {
        leer(pedir("POST", RESPUESTAS, token, Map.of("questionId", pregunta(clave), "textValue", valor)), 200);
    }

    private int pregunta(String clave) {
        return jdbc.queryForObject("SELECT id FROM renaser.preguntas_onboarding WHERE clave_pregunta = ?",
                Integer.class, clave);
    }

    private List<Pedido> operaciones(UUID persona) {
        return List.of(new Pedido("GET", MAPA, null), new Pedido("PUT", MAPA + "/acciones", acciones()),
                new Pedido("PUT", MAPA + "/reemplazos", reemplazos()), new Pedido("POST", MAPA + "/completar", null),
                new Pedido("GET", RESPUESTAS + "?flow=mapa_dia7", null),
                new Pedido("POST", RESPUESTAS, Map.of("questionId", pregunta("map_priority_area"), "textValue", "salud")),
                new Pedido("POST", MEDIA + "/upload-url", Map.of("kind", "DOCUMENTO", "contentType", "application/pdf")),
                new Pedido("POST", MEDIA, Map.of("kind", "DOCUMENTO", "bucket", "onboarding-media",
                        "path", "onboarding/" + persona + "/documento/mapa")),
                new Pedido("PUT", "/api/v1/rocks/master/CUERPO", objetivo()),
                new Pedido("POST", "/api/v1/habits", habito()));
    }

    private void assertSinDatosDelMapa(UUID persona) {
        for (String tabla : List.of("acciones_mapa", "protocolos_reemplazo_mapa", "respuestas_onboarding",
                "medias_onboarding", "etapas_onboarding_completadas", "participantes_programa")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser." + tabla + " WHERE usuario_id = ?",
                    Integer.class, persona)).as("no se escribió %s", tabla).isZero();
        }
    }

    private String iniciarSesion(UUID persona, String rol) throws Exception {
        HttpResponse<String> respuesta = login(persona);
        assertThat(leer(respuesta, 200).get("role").asText()).isEqualTo(rol);
        String token = respuesta.headers().firstValue("X-Auth-Token").orElseThrow();
        tokens.add(token);
        return token;
    }

    private HttpResponse<String> login(UUID persona) throws Exception {
        return pedir("POST", "/api/v1/auth/login", null,
                Map.of("email", persona + "@renaser.test", "contrasena", CLAVE));
    }

    private UUID usuario(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, hash_contrasena)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario), ?)
                """, id, id + "@renaser.test", "Mapa IT " + rol, rol, estado, encoder.encode(CLAVE));
        usuarios.add(id);
        return id;
    }

    private JsonNode leer(HttpResponse<String> respuesta, int codigo) throws Exception {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(codigo);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (token != null) pedido.header("X-Auth-Token", token);
        if (actorDelHeader != null) pedido.header("X-Actor-Id", actorDelHeader.toString());
        if (cuerpo == null) pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        else pedido.header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> acciones() {
        return Map.of("actions", List.of(Map.of("actionId", "caminar", "area", "salud",
                "text", "Caminar cuarenta minutos", "weeklyFrequency", 3, "days", List.of(1, 3, 5))));
    }

    private static Map<String, Object> reemplazos() {
        return Map.of("protocols", List.of(Map.of("protocolId", "pantalla", "pattern", "Postergar",
                "trigger", "Cansancio", "currentBehavior", "Mirar el teléfono",
                "alternativeResponse", "Salir a caminar")));
    }

    private static Map<String, Object> objetivo() {
        return Map.of("objetivo", "Cumplir mi objetivo del mapa en noventa días");
    }

    private static Map<String, Object> habito() {
        return Map.of("title", "Caminar cuarenta minutos", "habitType", "CHECKBOX", "category", "BODY",
                "template", "OTRO", "triggerTime", "09:00:00", "activeWeekdays", List.of("MONDAY", "WEDNESDAY", "FRIDAY"));
    }

    private record Pedido(String metodo, String ruta, Object cuerpo) { }
}

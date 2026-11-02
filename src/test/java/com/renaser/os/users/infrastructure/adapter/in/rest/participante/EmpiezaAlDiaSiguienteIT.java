package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.RelojDePrueba;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 036 (D-309, 2026-11-02) de punta a punta sobre el Tomcat real: el programa empieza SIEMPRE al día siguiente y la
 * persona ya no elige. El reloj es el {@link RelojDePrueba} compartido (regla 03, E-649) y se fija alrededor de la
 * medianoche de Lima, donde la fecha de UTC y la de la persona difieren (regla 02): 04:59 UTC del 3 de noviembre son las
 * 23:59 del 2 en Lima, y 05:01 UTC son las 00:01 del 3.
 *
 * <p>Cubre lo que decide el servidor para la app nueva (sin cuerpo = mañana) y para el APK anterior (que manda la fecha
 * que vio: mañana si la guardó hoy; 400 y vuelve a leer si ya no lo es).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EmpiezaAlDiaSiguienteIT {

    private static final Instant ONCE_Y_MEDIA_EN_LIMA = Instant.parse("2026-11-03T04:59:00Z"); // 23:59 del 2 en Lima
    private static final Instant PASADA_LA_MEDIANOCHE_EN_LIMA = Instant.parse("2026-11-03T05:01:00Z"); // 00:01 del 3
    private static final String ACTIVAR = "/api/v1/onboarding/activate-program";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RelojDePrueba reloj;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void fijarElReloj() {
        reloj.fijar(ONCE_Y_MEDIA_EN_LIMA);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa.
        usuarios.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("a las 23:59 de Lima el aprendiz ve una sola fecha, mañana de Lima, y sin cuerpo empieza ese día")
    void sinCuerpoEmpiezaManianaDeLimaYNoElDiaSiguienteDeUtc() throws Exception {
        UUID aprendiz = usuarioConFilaSinActivar("APRENDIZ");
        String sesion = sesionDe(aprendiz);

        JsonNode antes = leer(pedir("GET", sesion, null));
        assertThat(antes.get("activated").asBoolean()).isFalse();
        assertThat(antes.get("validStartDates")).extracting(JsonNode::asText).containsExactly("2026-11-03");

        JsonNode activada = leer(pedir("POST", sesion, null));

        assertThat(activada.get("startDate").asText()).isEqualTo("2026-11-03");
        assertThat(activada.get("programDay").asInt()).as("todavía no llegó: día 0").isZero();
        assertThat(inicioGuardado(aprendiz)).isEqualTo("2026-11-03");
    }

    @Test
    @DisplayName("pasada la medianoche de Lima mañana ya es otro día")
    void pasadaLaMedianocheMananaEsElDiaSiguiente() throws Exception {
        reloj.fijar(PASADA_LA_MEDIANOCHE_EN_LIMA);
        UUID aprendiz = usuarioConFilaSinActivar("APRENDIZ");
        String sesion = sesionDe(aprendiz);

        assertThat(leer(pedir("GET", sesion, null)).get("validStartDates")).extracting(JsonNode::asText)
                .containsExactly("2026-11-04");
        leer(pedir("POST", sesion, Map.of()));

        assertThat(inicioGuardado(aprendiz)).isEqualTo("2026-11-04");
    }

    @Test
    @DisplayName("la pantalla quedó abierta pasada la medianoche: 400 con la fecha nueva, nada se activa, y con la nueva sí")
    void laFechaQueYaNoEsManianaSeRechazaYSePuedeReintentarConLaNueva() throws Exception {
        UUID aprendiz = usuarioConFilaSinActivar("APRENDIZ");
        String sesion = sesionDe(aprendiz);
        String laQueSeVio = leer(pedir("GET", sesion, null)).get("validStartDates").get(0).asText();
        assertThat(laQueSeVio).isEqualTo("2026-11-03");

        reloj.fijar(PASADA_LA_MEDIANOCHE_EN_LIMA);
        HttpResponse<String> rechazada = pedir("POST", sesion, Map.of("startDate", laQueSeVio));

        assertThat(rechazada.statusCode()).as(rechazada.body()).isEqualTo(400);
        assertThat(rechazada.body()).contains("2026-11-04");
        assertThat(estaActivado(aprendiz)).as("no se activó con una fecha que ya no es mañana").isFalse();

        String laNueva = leer(pedir("GET", sesion, null)).get("validStartDates").get(0).asText();
        assertThat(leer(pedir("POST", sesion, Map.of("startDate", laNueva))).get("startDate").asText())
                .isEqualTo("2026-11-04");
    }

    @Test
    @DisplayName("el APK anterior mandaba +2 o +3: ahora es 400 y la fila queda sin activar")
    void lasFechasDeAntesSeRechazan() throws Exception {
        UUID aprendiz = usuarioConFilaSinActivar("APRENDIZ");
        String sesion = sesionDe(aprendiz);

        for (String fecha : List.of("2026-11-02", "2026-11-05", "2026-11-06")) {
            HttpResponse<String> respuesta = pedir("POST", sesion, Map.of("startDate", fecha));
            assertThat(respuesta.statusCode()).as(fecha + " " + respuesta.body()).isEqualTo(400);
        }
        assertThat(estaActivado(aprendiz)).isFalse();
    }

    @Test
    @DisplayName("activar dos veces es idempotente, también pasada la medianoche y con la fecha que se vio antes")
    void activarDosVecesNoMueveLaFecha() throws Exception {
        UUID aprendiz = usuarioConFilaSinActivar("APRENDIZ");
        String sesion = sesionDe(aprendiz);
        leer(pedir("POST", sesion, Map.of("startDate", "2026-11-03")));
        Instant activadoEn = jdbcTemplate.queryForObject(
                "SELECT programa_activado_en FROM renaser.participantes_programa WHERE usuario_id = ?",
                java.sql.Timestamp.class, aprendiz).toInstant();

        reloj.fijar(PASADA_LA_MEDIANOCHE_EN_LIMA);
        assertThat(leer(pedir("POST", sesion, null)).get("startDate").asText()).isEqualTo("2026-11-03");
        assertThat(leer(pedir("POST", sesion, Map.of("startDate", "2026-11-03"))).get("startDate").asText())
                .isEqualTo("2026-11-03");
        assertThat(pedir("POST", sesion, Map.of("startDate", "2026-11-04")).statusCode())
                .as("otra fecha sobre un programa ya activado: 409").isEqualTo(409);

        assertThat(inicioGuardado(aprendiz)).isEqualTo("2026-11-03");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT programa_activado_en FROM renaser.participantes_programa WHERE usuario_id = ?",
                java.sql.Timestamp.class, aprendiz).toInstant()).isEqualTo(activadoEn);
        assertThat(leer(pedir("GET", sesion, null)).get("validStartDates")).isEmpty();
    }

    @Test
    @DisplayName("la duración no cambia: un año sigue siendo un año, contado desde mañana")
    void laDuracionDeLaFormacionNoCambia() throws Exception {
        UUID aprendiz = usuarioConFilaSinActivar("APRENDIZ");
        jdbcTemplate.update("UPDATE renaser.participantes_programa SET formacion = 'DIPLOMADO', duracion_dias = 365 "
                + "WHERE usuario_id = ?", aprendiz);

        JsonNode activada = leer(pedir("POST", sesionDe(aprendiz), null));

        assertThat(activada.get("startDate").asText()).isEqualTo("2026-11-03");
        assertThat(activada.get("expectedGraduationDate").asText()).isEqualTo("2027-11-03");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT duracion_dias FROM renaser.participantes_programa WHERE usuario_id = ?",
                Integer.class, aprendiz)).isEqualTo(365);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private String inicioGuardado(UUID persona) {
        return jdbcTemplate.queryForObject(
                "SELECT fecha_inicio::text FROM renaser.participantes_programa WHERE usuario_id = ?", String.class, persona);
    }

    private boolean estaActivado(UUID persona) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT programa_activado_en IS NOT NULL FROM renaser.participantes_programa WHERE usuario_id = ?",
                Boolean.class, persona));
    }

    private JsonNode leer(HttpResponse<String> respuesta) throws Exception {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ACTIVAR))
                .header("X-Auth-Token", sesion);
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
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

    /** La fila como la deja el alta del aprendiz antes de activar: sin `programa_activado_en`. */
    private UUID usuarioConFilaSinActivar(String rol) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Fixture " + rol, rol);
        usuarios.add(id);
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 0)", id);
        return id;
    }
}

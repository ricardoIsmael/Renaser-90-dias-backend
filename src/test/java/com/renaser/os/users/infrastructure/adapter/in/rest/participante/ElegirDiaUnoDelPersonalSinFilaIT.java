package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
 * D-261 (decisión del dueño del 2026-10-07: «que elija el día como los demás») de punta a punta sobre
 * el Tomcat real: el personal SIN fila en {@code participantes_programa} elige su Día 1 por el mismo
 * camino que el aprendiz ({@code GET/POST /api/v1/onboarding/activate-program}); al elegir se le crea
 * la fila ya activada con esa fecha.
 *
 * <p><b>Reloj en la madrugada UTC (regla 02):</b> 03:30 UTC del 8 de octubre son las 22:30 del 7 en
 * Lima. Las fechas ofrecidas son 8, 9 y 10 (desde el 7 de Lima); contadas con la fecha del servidor
 * serían 9, 10 y 11.
 *
 * <p><b>Compatibilidad:</b> los APK 1.5.0 y anteriores siguen llamando {@code POST
 * /mentor/activate-tracking}, que tiene que seguir creando la fila activada HOY (D-07), como siempre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, ElegirDiaUnoDelPersonalSinFilaIT.RelojDeLaPrueba.class})
class ElegirDiaUnoDelPersonalSinFilaIT {

    private static final Instant MADRUGADA_UTC = Instant.parse("2026-10-08T03:30:00Z");
    private static final String ACTIVAR = "/api/v1/onboarding/activate-program";
    private static final String SEGUIMIENTO = "/api/v1/mentor/activate-tracking";
    private static final String CONTEXTO = "/api/v1/mentor/context";

    @TestConfiguration
    static class RelojDeLaPrueba {
        @Bean
        @Primary
        Clock relojEnLaMadrugadaUtc() {
            return FixedClock.at(MADRUGADA_UTC);
        }
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private LoadParticipacionProgramaPort participaciones;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa.
        usuarios.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @ParameterizedTest(name = "{0} sin fila elige su Día 1: se crea la fila con esa fecha y el Día 1 es ese día")
    @ValueSource(strings = {"MENTOR", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA"})
    void elPersonalSinFilaEligeSuDiaUno(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");
        String sesion = sesionDe(persona);

        JsonNode antes = leer(pedir("GET", ACTIVAR, sesion, null));
        assertThat(antes.get("activated").asBoolean()).isFalse();
        assertThat(antes.get("validStartDates")).extracting(JsonNode::asText)
                .containsExactly("2026-10-08", "2026-10-09", "2026-10-10");
        assertThat(filas(persona)).as("consultar las fechas no crea la fila").isZero();

        HttpResponse<String> activada = pedir("POST", ACTIVAR, sesion, Map.of("startDate", "2026-10-09"));

        assertThat(activada.statusCode()).as(activada.body()).isEqualTo(200);
        Map<String, Object> fila = jdbcTemplate.queryForMap("""
                SELECT fecha_inicio::text AS inicio, dias_ajuste_programa AS ajuste, dia_programa AS dia,
                       programa_activado_en IS NOT NULL AS activado, timezone AS zona
                FROM renaser.participantes_programa WHERE usuario_id = ?
                """, persona);
        assertThat(fila.get("inicio")).isEqualTo("2026-10-09");
        assertThat(((Number) fila.get("ajuste")).intValue()).isZero();
        assertThat(((Number) fila.get("dia")).intValue()).as("todavía no llegó: día 0, como el aprendiz").isZero();
        assertThat(fila.get("activado")).isEqualTo(true);
        assertThat(fila.get("zona")).isEqualTo("America/Lima");
        ParticipacionPrograma guardada = participaciones.byParticipanteId(UserId.of(persona)).orElseThrow();
        assertThat(guardada.diaVigente(FixedClock.at(Instant.parse("2026-10-09T04:59:00Z")))).isZero();
        assertThat(guardada.diaVigente(FixedClock.at(Instant.parse("2026-10-09T05:00:00Z")))).isEqualTo(1);

        JsonNode despues = leer(pedir("GET", ACTIVAR, sesion, null));
        assertThat(despues.get("activated").asBoolean()).isTrue();
        assertThat(despues.get("startDate").asText()).isEqualTo("2026-10-09");
        assertThat(leer(pedir("GET", CONTEXTO, sesion, null)).at("/capabilities/canStartProgram").asBoolean())
                .as("ya tiene programa: la invitación desaparece").isFalse();
        assertThat(pedir("POST", SEGUIMIENTO, sesion, null).statusCode())
                .as("«Empezar» de un APK viejo después de elegir: 409 como con cualquier fila").isEqualTo(409);
    }

    @ParameterizedTest(name = "{0} sin fila con una fecha fuera de las ofrecidas: 400 y no se crea la fila")
    @ValueSource(strings = {"MENTOR", "ADMIN"})
    void fechaFueraDeLasOfrecidasNoCreaLaFila(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");

        HttpResponse<String> hoy = pedir("POST", ACTIVAR, sesionDe(persona), Map.of("startDate", "2026-10-07"));

        assertThat(hoy.statusCode()).as(hoy.body()).isEqualTo(400);
        assertThat(filas(persona)).isZero();
    }

    @ParameterizedTest(name = "{0} suspendido sin fila: 403 y no se crea la fila")
    @ValueSource(strings = {"MENTOR", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA"})
    void suspendidoSinFilaRecibe403(String rol) throws Exception {
        UUID persona = usuario(rol, "SUSPENDIDO");
        String sesion = sesionDe(persona);

        assertThat(pedir("GET", ACTIVAR, sesion, null).statusCode()).isEqualTo(403);
        assertThat(pedir("POST", ACTIVAR, sesion, Map.of("startDate", "2026-10-08")).statusCode()).isEqualTo(403);
        assertThat(filas(persona)).isZero();
    }

    @Test
    @DisplayName("el aprendiz sin fila sigue igual: 404 al consultar y al elegir, y no se le crea fila")
    void elAprendizSinFilaSigueIgual() throws Exception {
        UUID aprendiz = usuario("APRENDIZ", "ACTIVO");
        String sesion = sesionDe(aprendiz);

        assertThat(pedir("GET", ACTIVAR, sesion, null).statusCode()).isEqualTo(404);
        assertThat(pedir("POST", ACTIVAR, sesion, Map.of("startDate", "2026-10-08")).statusCode()).isEqualTo(404);
        assertThat(pedir("POST", SEGUIMIENTO, sesion, null).statusCode()).isEqualTo(403);
        assertThat(filas(aprendiz)).isZero();
    }

    @ParameterizedTest(name = "{0}: «Empezar» de la 1.5.0 (activate-tracking) sigue creando la fila activada hoy")
    @ValueSource(strings = {"MENTOR", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA"})
    void activateTrackingSigueIgual(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");
        String sesion = sesionDe(persona);

        JsonNode creada = leer(pedir("POST", SEGUIMIENTO, sesion, null));

        assertThat(creada.get("programDay").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT fecha_inicio::text FROM renaser.participantes_programa WHERE usuario_id = ?",
                String.class, persona)).as("hoy en Lima, no la fecha UTC del servidor").isEqualTo("2026-10-07");
        assertThat(leer(pedir("GET", ACTIVAR, sesion, null)).get("activated").asBoolean()).isTrue();
        assertThat(pedir("POST", SEGUIMIENTO, sesion, null).statusCode()).isEqualTo(409);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private int filas(UUID persona) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.participantes_programa WHERE usuario_id = ?", Integer.class, persona);
        return n == null ? 0 : n;
    }

    private JsonNode leer(HttpResponse<String> respuesta) throws Exception {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
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

    private UUID usuario(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", "Fixture " + rol, rol, estado);
        usuarios.add(id);
        return id;
    }
}

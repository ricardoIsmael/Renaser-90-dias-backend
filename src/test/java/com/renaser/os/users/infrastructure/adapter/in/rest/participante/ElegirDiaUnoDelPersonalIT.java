package com.renaser.os.users.infrastructure.adapter.in.rest.participante;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-260 (2026-10-07) de punta a punta sobre el Tomcat real: el personal con la fila del programa sin
 * activar elige su Día 1 por el MISMO camino que el aprendiz ({@code GET/POST
 * /api/v1/onboarding/activate-program}), con la misma regla (las fechas que da el servidor) y el
 * programa empieza ese día.
 *
 * <p><b>Lo que esta prueba deja escrito:</b> el bloqueo que vio el dueño (una cuenta ADMIN con
 * «Todavía no elegiste tu Día 1» y ningún lugar donde elegirlo) no estaba en el servidor. El
 * endpoint nunca filtró por rol ({@code USE_APP}, self por construcción); la app no lo llamaba
 * fuera del onboarding, y la invitación de Hoy se esconde a quien ya tiene fila
 * ({@code canStartProgram = staff && !inscrito}). Si alguien agrega un guard de rol a este endpoint,
 * el personal vuelve a quedar sin salida, y esto falla.
 *
 * <p>El reloj es el real: las fechas salen del GET y se mandan tal cual, así que la prueba no
 * depende de la hora del día. La aritmética en la madrugada UTC está en
 * {@code RelojProgramaServiceTest} y {@code ParticipacionProgramaTest} con {@code FixedClock}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ElegirDiaUnoDelPersonalIT {

    private static final String ACTIVAR = "/api/v1/onboarding/activate-program";
    private static final String CONTEXTO = "/api/v1/mentor/context";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa.
        usuarios.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @ParameterizedTest(name = "{0} con la fila sin activar elige su Día 1 y el programa empieza ese día")
    @ValueSource(strings = {"MENTOR", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA"})
    void elPersonalConFilaSinActivarEligeSuDiaUno(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");
        filaSinActivar(persona);
        String sesion = sesionDe(persona);

        JsonNode antes = leer(pedir("GET", ACTIVAR, sesion, null));
        assertThat(antes.get("activated").asBoolean()).isFalse();
        assertThat(antes.get("validStartDates")).hasSize(3);
        // Por qué la invitación de Hoy no aparecía: ya tiene fila, así que no «puede empezar».
        assertThat(leer(pedir("GET", CONTEXTO, sesion, null)).at("/capabilities/canStartProgram").asBoolean())
                .isFalse();

        String elegida = antes.get("validStartDates").get(0).asText();
        HttpResponse<String> activada = pedir("POST", ACTIVAR, sesion, Map.of("startDate", elegida));

        assertThat(activada.statusCode()).as(activada.body()).isEqualTo(200);
        JsonNode despues = leer(pedir("GET", ACTIVAR, sesion, null));
        assertThat(despues.get("activated").asBoolean()).isTrue();
        assertThat(despues.get("startDate").asText()).isEqualTo(elegida);
        // «Ese mismo día comienza, no debe descontar nada»: la fecha elegida es el Día 1, sin ajuste.
        Map<String, Object> fila = jdbcTemplate.queryForMap("""
                SELECT fecha_inicio::text AS inicio, dias_ajuste_programa AS ajuste, dia_programa AS dia
                FROM renaser.participantes_programa WHERE usuario_id = ?
                """, persona);
        assertThat(fila.get("inicio")).isEqualTo(elegida);
        assertThat(((Number) fila.get("ajuste")).intValue()).isZero();
        assertThat(((Number) fila.get("dia")).intValue()).as("todavía no llegó: día 0, como el aprendiz").isZero();
    }

    @Test
    @DisplayName("el aprendiz sigue igual: con la fila sin activar elige su Día 1 por el mismo endpoint")
    void elAprendizSigueIgual() throws Exception {
        UUID aprendiz = usuario("APRENDIZ", "ACTIVO");
        filaSinActivar(aprendiz);
        String sesion = sesionDe(aprendiz);
        String elegida = leer(pedir("GET", ACTIVAR, sesion, null)).get("validStartDates").get(2).asText();

        HttpResponse<String> activada = pedir("POST", ACTIVAR, sesion, Map.of("startDate", elegida));

        assertThat(activada.statusCode()).as(activada.body()).isEqualTo(200);
        assertThat(leer(pedir("GET", ACTIVAR, sesion, null)).get("startDate").asText()).isEqualTo(elegida);
    }

    @ParameterizedTest(name = "{0} suspendido: 403 al elegir y la fila queda sin activar")
    @ValueSource(strings = {"MENTOR", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA", "APRENDIZ"})
    void suspendidoRecibe403(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");
        filaSinActivar(persona);
        String sesion = sesionDe(persona);
        String elegida = leer(pedir("GET", ACTIVAR, sesion, null)).get("validStartDates").get(0).asText();
        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", persona);

        HttpResponse<String> respuesta = pedir("POST", ACTIVAR, sesion, Map.of("startDate", elegida));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT programa_activado_en IS NULL FROM renaser.participantes_programa WHERE usuario_id = ?",
                Boolean.class, persona)).isTrue();
    }

    @ParameterizedTest(name = "{0} sin fila: el servidor dice que puede empezar (la app muestra la invitación, no el error)")
    @ValueSource(strings = {"MENTOR", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA"})
    void sinFilaPuedeEmpezar(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");

        JsonNode contexto = leer(pedir("GET", CONTEXTO, sesionDe(persona), null));

        assertThat(contexto.at("/capabilities/canStartProgram").asBoolean()).isTrue();
        assertThat(contexto.at("/capabilities/programRequired").asBoolean()).isFalse();
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

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

    /** La fila como la deja el alta (aprobada o invitada) antes de elegir: sin `programa_activado_en`. */
    private void filaSinActivar(UUID persona) {
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (?, 0)", persona);
    }
}

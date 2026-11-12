package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La matriz estricta de Mentor y Guía de punta a punta (spec 050, tramo H, FR-041): sesión de verdad, Spring Security, el interceptor
 * real, la configuración leída del {@code Environment}, Redis real (el modo fijado y el contador por día) y Postgres. Lo que no ve
 * ninguna prueba con dobles: que las dos propiedades se lean, que el modo cambie <b>sin reiniciar</b> con solo escribir la clave de
 * Redis que escribe {@code scripts/despliegue/permisos-modo.sh}, y que el contador guarde el rol y el endpoint, nunca la cuenta.
 *
 * <p>El contexto nace con {@code mentor-enforcement=true} (APLICAR por configuración) y relee Redis en cada pedido
 * ({@code releer-cada=PT0S}; en producción son 30 s). El endpoint de prueba es {@code GET /api/v1/admin/caja}: pide
 * {@code MANAGE_RENASER_BOX}, que no está en la lista, y su servicio responde con su propio 403 («Solo el Admin lleva la Caja
 * Renaser»), lo que permite distinguir el 403 del interceptor del del servicio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "renaser.security.mentor-enforcement=true",
        "renaser.security.mentor-enforcement-releer-cada=PT0S"})
@Import(TestcontainersConfiguration.class)
class MatrizDeMentorConRedisIT {

    private static final String CLAVE_DEL_MODO = "proteccion:permisos:modo";
    private static final String HASHES_DEL_DIA = "proteccion:permisos:denegaciones:";
    private static final String RUTA_DENEGADA = "/api/v1/admin/caja";
    private static final String RUTA_PERMITIDA = "/api/v1/mentor/context";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redis;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<UUID> usuarios = new ArrayList<>();

    @AfterEach
    void limpiar() {
        redis.delete(CLAVE_DEL_MODO);
        Set<String> claves = redis.keys(HASHES_DEL_DIA + "*");
        if (claves != null && !claves.isEmpty()) {
            redis.delete(claves);
        }
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    // spec 050 FR-041
    @Test
    @DisplayName("APLICAR por configuración: el mentor recibe el 403 del interceptor, y el contador guarda el rol y el endpoint, no la cuenta")
    void aplicarPorConfiguracion() throws Exception {
        UUID mentor = usuario("MENTOR");

        HttpResponse<String> respuesta = get(RUTA_DENEGADA, sesionDe(mentor));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.body()).contains("\"message\":\"No autorizado\"").contains("\"codigo\":\"SIN_PERMISO\"");
        Map<String, String> anotado = lasDenegaciones();
        assertThat(anotado).containsEntry("DENEGADO MENTOR MANAGE_RENASER_BOX GET " + RUTA_DENEGADA, "1");
        assertThat(String.join("|", anotado.keySet())).as("el registro no lleva la cuenta").doesNotContain(mentor.toString());
        assertThat(redis.getExpire(redis.keys(HASHES_DEL_DIA + "*").iterator().next()))
                .as("vive 30 días, no para siempre").isBetween(1L, Duration.ofDays(30).toSeconds());
    }

    // spec 050 FR-041
    @Test
    @DisplayName("SOMBRA fijada en Redis, sin reiniciar: el pedido llega al servicio (su propio 403), queda anotado «habría denegado» y borrar la clave vuelve a APLICAR")
    void elModoDeRedisCambiaSinReiniciar() throws Exception {
        String sesionDeLaGuia = sesionDe(usuario("GUIA_APRENDIZ"));

        redis.opsForValue().set(CLAVE_DEL_MODO, "SOMBRA");
        HttpResponse<String> enSombra = get(RUTA_DENEGADA, sesionDeLaGuia);

        assertThat(enSombra.body()).as("en sombra el interceptor no responde: responde el guard del servicio")
                .contains("Solo el Admin lleva la Caja Renaser").doesNotContain("\"message\":\"No autorizado\"");
        assertThat(lasDenegaciones()).containsEntry("HABRIA_DENEGADO TRAINEE_GUIDE MANAGE_RENASER_BOX GET " + RUTA_DENEGADA, "1");

        redis.delete(CLAVE_DEL_MODO);
        HttpResponse<String> deVueltaEnAplicar = get(RUTA_DENEGADA, sesionDeLaGuia);

        assertThat(deVueltaEnAplicar.statusCode()).isEqualTo(403);
        assertThat(deVueltaEnAplicar.body()).as("sin la clave rige la configuración (APLICAR)").contains("\"message\":\"No autorizado\"");
        assertThat(lasDenegaciones()).containsEntry("DENEGADO TRAINEE_GUIDE MANAGE_RENASER_BOX GET " + RUTA_DENEGADA, "1");
    }

    // spec 050 FR-041
    @Test
    @DisplayName("lo que la lista permite no se anota ni se niega, en APLICAR: la app de acompañamiento sigue entera")
    void loPermitidoPasa() throws Exception {
        String sesion = sesionDe(usuario("MENTOR"));

        HttpResponse<String> respuesta = get(RUTA_PERMITIDA, sesion);

        assertThat(respuesta.body()).doesNotContain("\"message\":\"No autorizado\"");
        assertThat(respuesta.statusCode()).isNotEqualTo(403);
        assertThat(lasDenegaciones()).isEmpty();
    }

    // spec 050 FR-041
    @Test
    @DisplayName("la ruta se anota con su plantilla (/{aprendizId}), no con el id concreto que pidió el mentor")
    void laRutaSeAnotaConSuPlantilla() throws Exception {
        UUID aprendiz = UUID.randomUUID();

        HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(
                                URI.create("http://localhost:" + puerto + "/api/v1/admin/caja/" + aprendiz + "/aprobar"))
                        .header("X-Auth-Token", sesionDe(usuario("MENTOR"))).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        Map<String, String> anotado = lasDenegaciones();
        assertThat(anotado).containsEntry("DENEGADO MENTOR MANAGE_RENASER_BOX POST /api/v1/admin/caja/{aprendizId}/aprobar", "1");
        assertThat(String.join("|", anotado.keySet())).doesNotContain(aprendiz.toString());
    }

    // spec 050 FR-041
    @Test
    @DisplayName("un valor de Redis que no es un modo (un error de tipeo) se ignora: rige la configuración y no se niega ni se abre nada por accidente")
    void unErrorDeTipeoNoCambiaElModo() throws Exception {
        redis.opsForValue().set(CLAVE_DEL_MODO, "APLICA");

        HttpResponse<String> respuesta = get(RUTA_DENEGADA, sesionDe(usuario("MENTOR")));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.body()).contains("\"message\":\"No autorizado\"");
    }

    /** Todos los campos de todos los hashes de denegaciones (uno por día), juntos. */
    private Map<String, String> lasDenegaciones() {
        Map<String, String> todas = new HashMap<>();
        Set<String> claves = redis.keys(HASHES_DEL_DIA + "*");
        if (claves != null) {
            for (String clave : claves) {
                redis.<String, String>opsForHash().entries(clave).forEach(todas::put);
            }
        }
        return todas;
    }

    private HttpResponse<String> get(String ruta, String sesion) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                        .header("X-Auth-Token", sesion).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private UUID usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Matriz IT " + rol, rol);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbcTemplate, id, rol);
        if ("MENTOR".equals(rol) || "GUIA_APRENDIZ".equals(rol)) {
            jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id);
        }
        usuarios.add(id);
        return id;
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
}

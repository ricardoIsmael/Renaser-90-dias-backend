package com.renaser.os.users.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alta y estado de una cuenta de punta a punta, contra lo que encontró el e2e del 2026-09-27: sesión de
 * verdad por {@code X-Auth-Token}, Spring Security, el interceptor, los casos de uso y Postgres real.
 *
 * <ul>
 *   <li><b>E-365 (SEG-08):</b> invitar con el id de una cuenta existente la pisaba entera.</li>
 *   <li><b>E-367 (ADM-20):</b> activar por estado una cuenta nunca aprobada la dejaba entrar sin programa.</li>
 *   <li><b>E-368 (HALLAZGO-A3):</b> borrar una solicitud pendiente dejaba al usuario huérfano y el correo
 *       bloqueado.</li>
 *   <li><b>E-369 (SEG-16):</b> la biografía y el departamento aceptaban 1 MB.</li>
 * </ul>
 * Cada prueba falla contra el código anterior a esos arreglos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AltaYEstadoDeCuentasIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> solicitudes = new ArrayList<>();
    private final Instant inicio = Instant.now();

    @AfterEach
    void limpiar() throws InterruptedException {
        esperarLosAvisosDeEstaPrueba();
        solicitudes.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.solicitudes_cuenta WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    /**
     * Invitar y reactivar publican eventos que otros módulos escuchan después del commit (el chat global
     * suma a la persona invitada). Si la limpieza borra la cuenta antes, el oyente choca con la FK y deja
     * su publicación incompleta en el outbox compartido, que otras pruebas de integración miran. Poll
     * corto, sin Awaitility (mismo criterio que {@code ResumenSemanalDelSemaforoIT}), acotado a lo que
     * publicó esta prueba.
     */
    private void esperarLosAvisosDeEstaPrueba() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (avisosPendientes() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(avisosPendientes()).as("publicaciones de esta prueba sin completar en el outbox").isZero();
    }

    /** Sin esquema a propósito: V2 crea la tabla en el esquema por defecto de la conexión, no en renaser. */
    private int avisosPendientes() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM event_publication "
                + "WHERE completion_date IS NULL AND publication_date >= ?", Integer.class,
                java.sql.Timestamp.from(inicio));
    }

    // ── E-365 (SEG-08) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("E-365: invitar con el id de una cuenta existente es 409 y la cuenta queda como estaba (ADMIN y ALCHEMIST)")
    void invitarNoPisaUnaCuentaExistente() throws Exception {
        UUID mentorSuspendido = usuario("MENTOR", "SUSPENDIDO", "Mentor Suspendido IT");
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", mentorSuspendido);
        Map<String, Object> antes = fila(mentorSuspendido);

        for (String rolQueInvita : List.of("ADMIN", "ALQUIMISTA")) {
            String sesion = sesionDe(usuario(rolQueInvita, "ACTIVO", "Invita " + rolQueInvita));

            HttpResponse<String> respuesta = enviar("POST", "/api/v1/users/invite", sesion, Map.of(
                    "usuarioId", mentorSuspendido.toString(),
                    "email", "pisado-" + UUID.randomUUID() + "@renaser.test",
                    "fullName", "Pisado Por Invitacion",
                    "role", "ADMIN"));

            assertThat(respuesta.statusCode()).as("invita un %s", rolQueInvita).isEqualTo(409);
            assertThat(respuesta.body()).contains("Ya existe una cuenta con ese id");
            assertThat(fila(mentorSuspendido)).as("la cuenta no cambió (invita un %s)", rolQueInvita).isEqualTo(antes);
        }
    }

    @Test
    @DisplayName("E-365: invitar con un correo que ya tiene cuenta es 409 y no crea nada")
    void invitarConUnCorreoTomadoEsUnConflicto() throws Exception {
        UUID existente = usuario("APRENDIZ", "ACTIVO", "Ya Registrada");
        String sesion = sesionDe(usuario("ADMIN", "ACTIVO", "Admin IT"));
        UUID nuevoId = UUID.randomUUID();

        HttpResponse<String> respuesta = enviar("POST", "/api/v1/users/invite", sesion, Map.of(
                "usuarioId", nuevoId.toString(), "email", existente + "@renaser.test",
                "fullName", "Otra Persona", "role", "MENTOR"));

        assertThat(respuesta.statusCode()).isEqualTo(409);
        assertThat(respuesta.body()).contains("Ya existe una cuenta con este correo");
        assertThat(cuantasConId(nuevoId)).isZero();
    }

    @Test
    @DisplayName("E-365: invitar a alguien nuevo sigue creando la cuenta (201)")
    void invitarAAlguienNuevoSigueFuncionando() throws Exception {
        String sesion = sesionDe(usuario("ADMIN", "ACTIVO", "Admin IT"));
        UUID nuevoId = UUID.randomUUID();
        usuarios.add(nuevoId);

        HttpResponse<String> respuesta = enviar("POST", "/api/v1/users/invite", sesion, Map.of(
                "usuarioId", nuevoId.toString(), "email", nuevoId + "@renaser.test",
                "fullName", "Mentora Nueva", "role", "MENTOR"));

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(fila(nuevoId)).containsEntry("rol", "MENTOR").containsEntry("estado", "ACTIVO");
    }

    // ── E-367 (ADM-20) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("E-367: activar o suspender por estado una cuenta que nunca se aprobó es 409; queda pendiente y sin programa")
    void elEstadoNoReemplazaAlAlta() throws Exception {
        UUID pendiente = usuario("APRENDIZ", "INACTIVO", "Nunca Aprobada");
        solicitudDe(pendiente);
        String sesion = sesionDe(usuario("ADMIN", "ACTIVO", "Admin IT"));

        for (String estado : List.of("ACTIVE", "SUSPENDED")) {
            HttpResponse<String> respuesta = enviar("PATCH", "/api/v1/admin/staff/" + pendiente + "/status", sesion,
                    Map.of("status", estado));

            assertThat(respuesta.statusCode()).as("pasar a %s", estado).isEqualTo(409);
            assertThat(respuesta.body()).contains("todavía no fue aprobada");
        }
        assertThat(fila(pendiente)).containsEntry("estado", "INACTIVO");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.participantes_programa WHERE usuario_id = ?",
                Integer.class, pendiente)).isZero();
    }

    @Test
    @DisplayName("E-367: reactivar a una cuenta suspendida sigue funcionando (204)")
    void reactivarAUnaSuspendidaSigueFuncionando() throws Exception {
        UUID suspendida = usuario("MENTOR", "SUSPENDIDO", "Mentor Suspendido IT");
        String sesion = sesionDe(usuario("ADMIN", "ACTIVO", "Admin IT"));

        HttpResponse<String> respuesta = enviar("PATCH", "/api/v1/admin/staff/" + suspendida + "/status", sesion,
                Map.of("status", "ACTIVE"));

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(fila(suspendida)).containsEntry("estado", "ACTIVO");
    }

    // ── E-368 (HALLAZGO-A3) ────────────────────────────────────────────────────

    @Test
    @DisplayName("E-368: borrar una solicitud pendiente borra la cuenta sin aprobar y el correo queda libre para pedir de nuevo")
    void borrarUnaSolicitudPendienteLiberaElCorreo() throws Exception {
        UUID pendiente = usuario("APRENDIZ", "INACTIVO", "Pide De Nuevo");
        UUID solicitud = solicitudDe(pendiente);
        String sesion = sesionDe(usuario("ADMIN", "ACTIVO", "Admin IT"));

        assertThat(enviar("DELETE", "/api/v1/account-requests/" + solicitud, sesion, null).statusCode()).isEqualTo(204);

        assertThat(cuantasConId(pendiente)).as("la cuenta que nunca se aprobó ya no está").isZero();
        HttpResponse<String> consulta = enviar("POST", "/api/v1/account-requests/check-email", null,
                Map.of("email", pendiente + "@renaser.test"));
        assertThat(consulta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(consulta.body()).get("available").asBoolean()).as("el correo quedó libre").isTrue();
    }

    @Test
    @DisplayName("E-368: borrar una solicitud ya aprobada no toca la cuenta, que es de verdad")
    void borrarUnaSolicitudAprobadaNoBorraLaCuenta() throws Exception {
        UUID aprobada = usuario("APRENDIZ", "ACTIVO", "Ya Aprobada");
        UUID solicitud = solicitudDe(aprobada);
        jdbcTemplate.update("UPDATE renaser.solicitudes_cuenta SET estado = 'APROBADA' WHERE id = ?", solicitud);
        String sesion = sesionDe(usuario("ADMIN", "ACTIVO", "Admin IT"));

        assertThat(enviar("DELETE", "/api/v1/account-requests/" + solicitud, sesion, null).statusCode()).isEqualTo(204);

        assertThat(fila(aprobada)).containsEntry("estado", "ACTIVO");
    }

    // ── E-369 (SEG-16) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("E-369: una biografía o un departamento de 1 MB es 400 con el motivo, y no se guarda nada")
    void laBiografiaYElDepartamentoTienenTope() throws Exception {
        UUID alquimista = usuario("ALQUIMISTA", "ACTIVO", "Alquimista IT");
        String sesion = sesionDe(alquimista);
        String unMega = "a".repeat(1_048_576);

        HttpResponse<String> bio = enviar("PATCH", "/api/v1/users/me", sesion,
                Map.of("fullName", "Alquimista IT", "bio", unMega));
        HttpResponse<String> departamento = enviar("PATCH", "/api/v1/users/me", sesion,
                Map.of("fullName", "Alquimista IT", "department", unMega));

        assertThat(bio.statusCode()).isEqualTo(400);
        assertThat(json.readTree(bio.body()).get("message").asText())
                .isEqualTo("La biografía no puede pasar de 1000 caracteres");
        assertThat(departamento.statusCode()).isEqualTo(400);
        assertThat(json.readTree(departamento.body()).get("message").asText())
                .isEqualTo("El departamento no puede pasar de 120 caracteres");
        assertThat(fila(alquimista)).containsEntry("bio", null).containsEntry("departamento", null);

        HttpResponse<String> corta = enviar("PATCH", "/api/v1/users/me", sesion,
                Map.of("fullName", "Alquimista IT", "bio", "Acompaño el proceso.", "department", "Operaciones"));
        assertThat(corta.statusCode()).isEqualTo(204);
        assertThat(fila(alquimista)).containsEntry("bio", "Acompaño el proceso.").containsEntry("departamento", "Operaciones");
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private HttpResponse<String> enviar(String metodo, String ruta, String sesion, Map<String, ?> cuerpo)
            throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8));
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json");
        }
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> fila(UUID id) {
        return jdbcTemplate.queryForMap("""
                SELECT email, nombre_completo, rol::text AS rol, estado::text AS estado, bio, departamento
                FROM renaser.usuarios WHERE id = ?
                """, id);
    }

    private int cuantasConId(UUID id) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.usuarios WHERE id = ?", Integer.class, id);
    }

    private UUID usuario(String rol, String estado, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        usuarios.add(id);
        return id;
    }

    /** La solicitud tal como la deja el alta: pendiente, con el mismo correo que su usuario. */
    private UUID solicitudDe(UUID usuario) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.solicitudes_cuenta (id, usuario_id, email, nombre_completo)
                VALUES (?, ?, ?, 'Solicitante IT')
                """, id, usuario, usuario + "@renaser.test");
        solicitudes.add(id);
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

package com.renaser.os.users.application.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-267 (spec 002, FR-022, contrato §9): <b>ningún endpoint da, quita, invita ni aprueba con el rol Soporte TI</b>, y
 * Administración no suspende ni elimina esa cuenta. De punta a punta, con sesión real por {@code X-Auth-Token},
 * Spring Security, el interceptor, los casos de uso y Postgres con V100. Lo prueban ADMIN y ALQUIMISTA, los dos roles
 * que sí manejan roles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SoporteTiNoSeAsignaIT {

    private static final String CONTRASENA = "Secreta123!clave";
    private static final List<String> QUIENES_MANEJAN_ROLES = List.of("ADMIN", "ALQUIMISTA");

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SubmitAccountRequestUseCase submit;
    @Autowired
    private TokenVerificacionEmailPort tokens;
    @Autowired
    private BorradoDefinitivoService borrado;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> cuentas = new ArrayList<>();

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE email LIKE 'invitado-ti-%@renaser.dev'");
        cuentas.forEach(id -> borrado.borrar(UserId.of(id), RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN, null));
    }

    @Test
    @DisplayName("cambiar el rol de alguien a IT_SUPPORT por PATCH /users/{id}/role es 400 y el rol no cambia")
    void nadieCambiaElRolASoporteTi() throws Exception {
        UUID aprendiz = usuario("APRENDIZ");
        for (String rol : QUIENES_MANEJAN_ROLES) {
            HttpResponse<String> respuesta = enviar("PATCH", "/api/v1/users/" + aprendiz + "/role", sesionDe(usuario(rol)),
                    Map.of("newRole", "IT_SUPPORT"));

            assertThat(respuesta.statusCode()).as("lo pide un %s", rol).isEqualTo(400);
        }
        assertThat(rolDe(aprendiz)).isEqualTo("APRENDIZ");
    }

    @Test
    @DisplayName("aprobar una solicitud con el rol IT_SUPPORT es 400 y la solicitud sigue pendiente")
    void nadieApruebaComoSoporteTi() throws Exception {
        UUID solicitud = solicitud();
        UUID usuario = jdbc.queryForObject("SELECT usuario_id FROM renaser.solicitudes_cuenta WHERE id = ?", UUID.class,
                solicitud);
        for (String rol : QUIENES_MANEJAN_ROLES) {
            HttpResponse<String> respuesta = enviar("POST", "/api/v1/account-requests/" + solicitud + "/approve",
                    sesionDe(usuario(rol)), Map.of("role", "IT_SUPPORT"));

            assertThat(respuesta.statusCode()).as("lo aprueba un %s", rol).isEqualTo(400);
        }
        assertThat(rolDe(usuario)).isEqualTo("APRENDIZ");
        assertThat(jdbc.queryForObject("SELECT estado::text FROM renaser.usuarios WHERE id = ?", String.class, usuario))
                .isEqualTo("INACTIVO");
    }

    @Test
    @DisplayName("invitar con el rol IT_SUPPORT, por /users/invite o por /admin/staff/invite, es 400 y no crea a nadie")
    void nadieInvitaComoSoporteTi() throws Exception {
        for (String rol : QUIENES_MANEJAN_ROLES) {
            String sesion = sesionDe(usuario(rol));
            String correo = "invitado-ti-" + UUID.randomUUID() + "@renaser.dev";

            HttpResponse<String> porUsuarios = enviar("POST", "/api/v1/users/invite", sesion, Map.of(
                    "usuarioId", UUID.randomUUID().toString(), "email", correo, "fullName", "Invitado TI",
                    "role", "IT_SUPPORT"));
            HttpResponse<String> porStaff = enviar("POST", "/api/v1/admin/staff/invite", sesion, Map.of(
                    "email", correo, "fullName", "Invitado TI", "role", "IT_SUPPORT"));

            assertThat(porUsuarios.statusCode()).as("/users/invite de un %s", rol).isIn(400, 403);
            assertThat(porStaff.statusCode()).as("/admin/staff/invite de un %s", rol).isIn(400, 403);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.usuarios WHERE email = ?", Integer.class,
                    correo)).isZero();
        }
    }

    @Test
    @DisplayName("PUT /admin/staff/{id} con un «role» en el cuerpo no cambia el rol: el pedido no lo lee")
    void editarElPerfilNoAsignaElRol() throws Exception {
        UUID mentor = usuario("MENTOR");

        HttpResponse<String> respuesta = enviar("PUT", "/api/v1/admin/staff/" + mentor, sesionDe(usuario("ADMIN")),
                Map.of("fullName", "Mentor Renombrado", "role", "IT_SUPPORT"));

        assertThat(respuesta.statusCode()).isBetween(200, 299);
        assertThat(rolDe(mentor)).isEqualTo("MENTOR");
    }

    @Test
    @DisplayName("el rol de la cuenta Soporte TI no se cambia, no se suspende y no se elimina desde Administración (403)")
    void administracionNoTocaLaCuentaSoporteTi() throws Exception {
        UUID soporte = usuario("SOPORTE_TI");
        String correo = jdbc.queryForObject("SELECT email FROM renaser.usuarios WHERE id = ?", String.class, soporte);
        for (String rol : QUIENES_MANEJAN_ROLES) {
            String sesion = sesionDe(usuario(rol));

            assertThat(enviar("PATCH", "/api/v1/users/" + soporte + "/role", sesion, Map.of("newRole", "ADMIN"))
                    .statusCode()).as("cambiar el rol, %s", rol).isEqualTo(403);
            assertThat(enviar("PATCH", "/api/v1/admin/staff/" + soporte + "/status", sesion,
                    Map.of("status", "SUSPENDED")).statusCode()).as("suspender, %s", rol).isEqualTo(403);
            assertThat(enviar("POST", "/api/v1/admin/users/" + soporte + "/account-deletion", sesion,
                    Map.of("confirmEmail", correo)).statusCode()).as("eliminar, %s", rol).isEqualTo(403);
        }
        assertThat(jdbc.queryForMap("SELECT rol::text AS rol, estado::text AS estado FROM renaser.usuarios WHERE id = ?",
                soporte)).containsEntry("rol", "SOPORTE_TI").containsEntry("estado", "ACTIVO");
    }

    // ── Semilla y pedidos ──────────────────────────────────────────────────

    private UUID solicitud() {
        String email = "rol-ti-" + UUID.randomUUID() + "@renaser.dev";
        String ip = "203.0.113." + ThreadLocalRandom.current().nextInt(1, 255);
        UUID id = submit.submit(SubmitAccountRequestCommand.porFormulario(email, "Pide Soporte", "+51 999 888 777",
                "Lima", tokens.generar(email, Duration.ofMinutes(30)), CONTRASENA, ip)).value();
        cuentas.add(jdbc.queryForObject("SELECT usuario_id FROM renaser.solicitudes_cuenta WHERE id = ?", UUID.class, id));
        return id;
    }

    private UUID usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Fixture " + rol, rol);
        cuentas.add(id);
        return id;
    }

    private String rolDe(UUID id) {
        return jdbc.queryForObject("SELECT rol::text FROM renaser.usuarios WHERE id = ?", String.class, id);
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String sesion, Map<String, ?> cuerpo)
            throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo),
                        StandardCharsets.UTF_8))
                .header("X-Auth-Token", sesion)
                .header("Content-Type", "application/json")
                .build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

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

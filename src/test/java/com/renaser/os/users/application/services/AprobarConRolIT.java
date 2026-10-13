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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-265 de punta a punta: aprobar una solicitud eligiendo el rol, con sesión real por {@code X-Auth-Token}, Spring
 * Security, el interceptor, los casos de uso y Postgres con V97. El alta es la de producción (submit con token de
 * correo); la limpieza, el borrado definitivo de verdad, porque aprobar despierta oyentes que dejan filas en otros
 * módulos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AprobarConRolIT {

    private static final String CONTRASENA = "Secreta123!clave";

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
    private final Instant inicio = Instant.now();

    @AfterEach
    void limpiar() throws InterruptedException {
        esperarLosAvisosDeEstaPrueba();
        cuentas.forEach(id -> borrado.borrar(UserId.of(id), RegistroDeEliminacion.Accion.ELIMINADA_POR_ADMIN, null));
    }

    @Test
    @DisplayName("aprobar como Guía de aprendiz: entra activa con ese rol, perfil de mentor y su programa, como si se "
            + "le hubiera cambiado el rol en «Equipo y roles»")
    void aprobarComoGuia() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Guia Nueva");

        HttpResponse<String> respuesta = aprobar(solicitud.id(), sesion, Map.of("role", "TRAINEE_GUIDE"));

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "GUIA_APRENDIZ").containsEntry("estado", "ACTIVO");
        assertThat(contar("perfiles_mentor", solicitud.usuario())).as("el perfil que recibe todo mentor").isEqualTo(1);
        assertThat(contar("participantes_programa", solicitud.usuario())).isEqualTo(1);
        esperarLosAvisosDeEstaPrueba();
        assertThat(jdbc.queryForList("SELECT rol FROM renaser.historial_rol_participante WHERE usuario_id = ? "
                + "ORDER BY rige_desde", String.class, solicitud.usuario()))
                .as("hábitos anotó el cambio de rol").containsExactly("TRAINEE", "TRAINEE_GUIDE");
    }

    @Test
    @DisplayName("aprobar como Administrador: entra a los chats de soporte que ya hay y los dos oyentes del chat "
            + "(registro y cambio de rol) terminan sin chocar — antes uno quedaba incompleto en el outbox")
    void aprobarComoAdministrador() throws Exception {
        UUID soporte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre) VALUES (?, 'SOPORTE', ?, 'Soporte IT')",
                soporte, "soporte|" + soporte);
        try {
            String sesion = sesionDe(usuario("ADMIN"));
            Solicitud solicitud = solicitud("Admin Nuevo");

            assertThat(aprobar(solicitud.id(), sesion, Map.of("role", "ADMIN")).statusCode()).isEqualTo(204);

            esperarLosAvisosDeEstaPrueba();
            assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "ADMIN");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.participantes_conversacion "
                    + "WHERE conversacion_id = ? AND usuario_id = ?", Integer.class, soporte, solicitud.usuario()))
                    .as("sumado al soporte que ya existía, una sola vez").isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM renaser.participantes_conversacion WHERE conversacion_id = ?", soporte);
            jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", soporte);
        }
    }

    @Test
    @DisplayName("sin cuerpo (app 1.5.0 y panel de solicitudes) entra como aprendiz, sin perfil de mentor")
    void sinRolEntraComoAprendiz() throws Exception {
        String sesion = sesionDe(usuario("ALQUIMISTA"));
        Solicitud solicitud = solicitud("Aprendiz Nueva");

        assertThat(aprobar(solicitud.id(), sesion, null).statusCode()).isEqualTo(204);

        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "APRENDIZ").containsEntry("estado", "ACTIVO");
        assertThat(contar("perfiles_mentor", solicitud.usuario())).isZero();
    }

    @Test
    @DisplayName("un rol que no existe es 400 y la solicitud sigue pendiente")
    void rolInexistenteEs400() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Rol Raro");

        HttpResponse<String> respuesta = aprobar(solicitud.id(), sesion, Map.of("role", "GUIA"));

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "APRENDIZ").containsEntry("estado", "INACTIVO");
    }

    @Test
    @DisplayName("un mentor o un guía no aprueban (403), aunque pidan su propio rol, y nada cambia")
    void elRolDeStaffNoAlcanzaParaAprobar() throws Exception {
        Solicitud solicitud = solicitud("Sin Aprobar");
        for (String rol : List.of("MENTOR", "GUIA_APRENDIZ")) {
            String sesion = sesionDe(usuario(rol));

            HttpResponse<String> respuesta = aprobar(solicitud.id(), sesion, Map.of("role", "ADMIN"));

            assertThat(respuesta.statusCode()).as("aprueba un %s", rol).isEqualTo(403);
        }
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "APRENDIZ").containsEntry("estado", "INACTIVO");
        assertThat(contar("participantes_programa", solicitud.usuario())).isZero();
    }

    @Test
    @DisplayName("el rol también se puede cambiar a Guía de aprendiz después, por PATCH /users/{id}/role")
    void cambiarARolDeGuia() throws Exception {
        String sesion = sesionDe(usuario("ADMIN"));
        Solicitud solicitud = solicitud("Pasa A Guia");
        assertThat(aprobar(solicitud.id(), sesion, null).statusCode()).isEqualTo(204);

        HttpResponse<String> respuesta = enviar("PATCH", "/api/v1/users/" + solicitud.usuario() + "/role", sesion,
                Map.of("newRole", "TRAINEE_GUIDE"));

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(rolYEstado(solicitud.usuario())).containsEntry("rol", "GUIA_APRENDIZ");
        assertThat(contar("perfiles_mentor", solicitud.usuario())).isEqualTo(1);
    }

    // ── Semilla y pedidos ──────────────────────────────────────────────────

    private record Solicitud(UUID id, UUID usuario) {
    }

    /** El alta de producción: usuario INACTIVO con su contraseña y la solicitud pendiente. */
    private Solicitud solicitud(String nombre) {
        String email = "rol-" + UUID.randomUUID() + "@renaser.dev";
        String ip = "203.0.113." + ThreadLocalRandom.current().nextInt(1, 255);
        UUID id = submit.submit(SubmitAccountRequestCommand.porFormulario(email, nombre, "+51 999 888 777", "Lima",
                tokens.generar(email, Duration.ofMinutes(30)), CONTRASENA, ip)).value();
        UUID usuario = jdbc.queryForObject("SELECT usuario_id FROM renaser.solicitudes_cuenta WHERE id = ?", UUID.class, id);
        cuentas.add(usuario);
        return new Solicitud(id, usuario);
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

    private HttpResponse<String> aprobar(UUID solicitud, String sesion, Map<String, ?> cuerpo) throws Exception {
        return enviar("POST", "/api/v1/account-requests/" + solicitud + "/approve", sesion, cuerpo);
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String sesion, Map<String, ?> cuerpo)
            throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                .header("X-Auth-Token", sesion);
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json");
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> rolYEstado(UUID id) {
        return jdbc.queryForMap("SELECT rol::text AS rol, estado::text AS estado FROM renaser.usuarios WHERE id = ?", id);
    }

    private int contar(String tabla, UUID usuario) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser." + tabla + " WHERE usuario_id = ?", Integer.class,
                usuario);
    }

    /** Aprobar publica eventos que otros módulos atienden después del commit; se espera a que terminen. */
    private void esperarLosAvisosDeEstaPrueba() throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (avisosPendientes() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(avisosPendientes()).as("publicaciones de esta prueba sin completar en el outbox").isZero();
    }

    private int avisosPendientes() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication "
                + "WHERE completion_date IS NULL AND publication_date >= ?", Integer.class,
                java.sql.Timestamp.from(inicio));
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

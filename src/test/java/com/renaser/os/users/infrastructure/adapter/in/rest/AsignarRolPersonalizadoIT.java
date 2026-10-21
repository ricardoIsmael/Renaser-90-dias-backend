package com.renaser.os.users.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.CrearRolPersonalizadoCommand;
import com.renaser.os.users.api.GestionarRolesPersonalizadosUseCase.EditarRolPersonalizadoCommand;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asignar un rol personalizado desde Administración y verlo en la API (spec 002, FR-031 y FR-032; contrato §4.5;
 * D-272), de punta a punta: sesión real, los interceptores, los casos de uso y Postgres con V115. Cubre
 * {@code PATCH /users/{id}/role} con {@code rolPersonalizadoId}, {@code rolPersonalizado} en {@code /auth/me},
 * {@code POST /users/me}, las listas de Administración y {@code GET /admin/roles-asignables} con su autorización.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AsignarRolPersonalizadoIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private GestionarRolesPersonalizadosUseCase gestion;

    private PedidosDePrueba pedidos;
    private UserId soporte;
    private final List<UUID> roles = new ArrayList<>();
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporte = UserId.of(pedidos.usuario("SOPORTE_TI"));
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("UPDATE renaser.usuarios SET rol_personalizado_id = NULL WHERE rol_personalizado_id = ANY (?)",
                (Object) roles.toArray(UUID[]::new));
        roles.forEach(id -> jdbc.update("DELETE FROM renaser.roles_personalizados WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.historial_rol_participante WHERE rige_desde >= ?",
                java.sql.Timestamp.from(inicio));
        pedidos.borrarCuentas();
    }

    private UUID rol(String nombre, UserRole base) {
        UUID id = gestion.crear(new CrearRolPersonalizadoCommand(soporte, nombre, null, base, Set.of("muro")))
                .despues().id();
        roles.add(id);
        return id;
    }

    private HttpResponse<String> asignar(String sesion, UUID cuenta, Map<String, Object> cuerpo) throws Exception {
        return pedidos.enviar("PATCH", "/api/v1/users/" + cuenta + "/role", sesion, cuerpo);
    }

    @Test
    @DisplayName("un ADMIN le asigna «Mentor junior» a un aprendiz: queda MENTOR y el nombre sale en /auth/me, "
            + "POST /users/me, Personas y Equipo y roles; quitarlo con newRole lo borra")
    void asignarYVerlo() throws Exception {
        UUID mentorJunior = rol("Mentor junior", UserRole.MENTOR);
        String admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        UUID cuenta = pedidos.usuario("APRENDIZ");
        String suSesion = pedidos.sesionDe(cuenta);

        assertThat(asignar(admin, cuenta, Map.of("rolPersonalizadoId", mentorJunior)).statusCode()).isEqualTo(204);

        JsonNode me = pedidos.json(pedidos.enviar("GET", "/api/v1/auth/me", suSesion, null));
        assertThat(me.get("role").asText()).as("role sigue siendo el rol base").isEqualTo("MENTOR");
        assertThat(me.get("rolPersonalizado").get("id").asText()).isEqualTo(mentorJunior.toString());
        assertThat(me.get("rolPersonalizado").get("nombre").asText()).isEqualTo("Mentor junior");
        assertThat(pedidos.json(pedidos.enviar("POST", "/api/v1/users/me", suSesion, null))
                .get("rolPersonalizado").get("nombre").asText()).isEqualTo("Mentor junior");
        assertThat(filaDe(pedidos.json(pedidos.enviar("GET", "/api/v1/admin/trainees?size=200&role=MENTOR", admin,
                null)), cuenta).get("rolPersonalizado").get("nombre").asText()).isEqualTo("Mentor junior");
        assertThat(filaDe(pedidos.json(pedidos.enviar("GET", "/api/v1/admin/staff?role=MENTOR&size=200", admin,
                null)), cuenta).get("rolPersonalizado").get("nombre").asText()).isEqualTo("Mentor junior");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.perfiles_mentor WHERE usuario_id = ?",
                Integer.class, cuenta)).as("como cualquier mentor nuevo, recibe su perfil").isEqualTo(1);

        assertThat(asignar(admin, cuenta, Map.of("newRole", "MENTOR")).statusCode()).isEqualTo(204);

        JsonNode sinRol = pedidos.json(pedidos.enviar("GET", "/api/v1/auth/me", suSesion, null));
        assertThat(sinRol.get("role").asText()).isEqualTo("MENTOR");
        assertThat(sinRol.get("rolPersonalizado").isNull()).isTrue();
    }

    @Test
    @DisplayName("un rol desactivado no se muestra en /auth/me y no se asigna (400); uno que no existe tampoco (400)")
    void desactivadoOInexistente() throws Exception {
        UUID mentorJunior = rol("Mentor junior", UserRole.MENTOR);
        String admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        UUID cuenta = pedidos.usuario("APRENDIZ");
        assertThat(asignar(admin, cuenta, Map.of("rolPersonalizadoId", mentorJunior)).statusCode()).isEqualTo(204);

        gestion.editar(new EditarRolPersonalizadoCommand(soporte, mentorJunior, "Mentor junior", null, null,
                Set.of("muro"), false, 1));

        assertThat(pedidos.json(pedidos.enviar("GET", "/api/v1/auth/me", pedidos.sesionDe(cuenta), null))
                .get("rolPersonalizado").isNull()).isTrue();
        UUID otra = pedidos.usuario("APRENDIZ");
        assertThat(asignar(admin, otra, Map.of("rolPersonalizadoId", mentorJunior)).statusCode()).isEqualTo(400);
        assertThat(asignar(admin, otra, Map.of("rolPersonalizadoId", UUID.randomUUID())).statusCode())
                .isEqualTo(400);
        assertThat(asignar(admin, otra, Map.of()).statusCode()).as("sin newRole ni rolPersonalizadoId")
                .isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT rol::text FROM renaser.usuarios WHERE id = ?", String.class, otra))
                .isEqualTo("APRENDIZ");
    }

    @Test
    @DisplayName("un rol que hereda de ADMIN lo asigna solo un ADMIN: el ALQUIMISTA recibe 403 y nada cambia")
    void soloAdminAsignaLoQueHeredaDeAdmin() throws Exception {
        UUID adminAcotado = rol("Admin acotado", UserRole.ADMIN);
        UUID cuenta = pedidos.usuario("APRENDIZ");

        assertThat(asignar(pedidos.sesionDe(pedidos.usuario("ALQUIMISTA")), cuenta,
                Map.of("rolPersonalizadoId", adminAcotado)).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT rol::text FROM renaser.usuarios WHERE id = ?", String.class, cuenta))
                .isEqualTo("APRENDIZ");

        assertThat(asignar(pedidos.sesionDe(pedidos.usuario("ADMIN")), cuenta,
                Map.of("rolPersonalizadoId", adminAcotado)).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT rol::text FROM renaser.usuarios WHERE id = ?", String.class, cuenta))
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("roles asignables: los seis base y los personalizados activos, para ADMIN y ALQUIMISTA")
    void rolesAsignables() throws Exception {
        UUID activo = rol("Mentor junior", UserRole.MENTOR);
        UUID apagado = rol("Viejo", UserRole.TRAINEE);
        gestion.editar(new EditarRolPersonalizadoCommand(soporte, apagado, "Viejo", null, null, Set.of(), false, 1));

        for (String rol : List.of("ADMIN", "ALQUIMISTA")) {
            HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/admin/roles-asignables",
                    pedidos.sesionDe(pedidos.usuario(rol)), null);
            assertThat(respuesta.statusCode()).as(rol).isEqualTo(200);
            List<String> claves = new ArrayList<>();
            pedidos.json(respuesta).forEach(fila -> claves.add(fila.get("clave").asText()));
            assertThat(claves).containsExactly("TRAINEE", "TRAINEE_GUIDE", "MENTOR", "MENTOR_LEAD", "ADMIN",
                    "ALCHEMIST", "rp:" + activo);
            JsonNode ultimo = pedidos.json(respuesta).get(6);
            assertThat(ultimo.get("rolBase").asText()).isEqualTo("MENTOR");
            assertThat(ultimo.get("rolPersonalizadoId").asText()).isEqualTo(activo.toString());
            assertThat(pedidos.json(respuesta).get(0).get("rolPersonalizadoId").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("roles asignables: MENTOR, GUÍA, LÍDER, APRENDIZ, Soporte TI y un ADMIN suspendido reciben 403")
    void rolesAsignablesAutorizacionNegativa() throws Exception {
        Map<String, String> cuentas = new HashMap<>();
        for (String rol : List.of("MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES", "APRENDIZ", "SOPORTE_TI")) {
            cuentas.put(rol, pedidos.sesionDe(pedidos.usuario(rol)));
        }
        cuentas.put("ADMIN suspendido", pedidos.sesionDe(pedidos.usuario("ADMIN", "SUSPENDIDO")));

        cuentas.forEach((quien, sesion) -> {
            try {
                assertThat(pedidos.enviar("GET", "/api/v1/admin/roles-asignables", sesion, null).statusCode())
                        .as(quien).isEqualTo(403);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static JsonNode filaDe(JsonNode pagina, UUID cuenta) {
        for (JsonNode fila : pagina.get("content")) {
            if (fila.get("id").asText().equals(cuenta.toString())) {
                return fila;
            }
        }
        throw new AssertionError("La cuenta " + cuenta + " no está en la lista");
    }
}

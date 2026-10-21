package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-278 (spec 006) de punta a punta: {@code capabilities.canExportRanking} de {@code GET /api/v1/mentor/context}.
 * Solo el líder de mentores, el administrador, el alquimista y Soporte TI pueden descargar la imagen del podio; el
 * mentor, el guía de aprendiz y el aprendiz no. El permiso lo decide el servidor, y MENTOR y TRAINEE_GUIDE son
 * justo los roles cuya matriz general falla abierta (A-1): esta prueba es la que frena que un permiso nuevo les
 * llegue sin que el dueño lo decida.
 *
 * <p>Una cuenta suspendida no llega a leer la capacidad: el contexto responde 403, como siempre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CapacidadExportarRankingIT {

    private static final String CONTEXTO = "/api/v1/mentor/context";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcClient jdbcClient;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbcClient.sql("DELETE FROM renaser.usuarios WHERE id = :id").param("id", id).update());
    }

    @ParameterizedTest(name = "{0} puede descargar el podio: {1}")
    @CsvSource({"LIDER_MENTORES,true", "ADMIN,true", "ALQUIMISTA,true", "SOPORTE_TI,true",
            "MENTOR,false", "GUIA_APRENDIZ,false", "APRENDIZ,false"})
    void laCapacidadSigueLaMatrizExplicita(String rol, boolean puede) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");

        JsonNode contexto = leer(pedir(sesionDe(persona)));

        assertThat(contexto.at("/capabilities/canExportRanking").isBoolean()).as("el campo viaja siempre").isTrue();
        assertThat(contexto.at("/capabilities/canExportRanking").asBoolean()).isEqualTo(puede);
    }

    @Test
    @DisplayName("las capacidades de siempre no cambian al sumar la nueva")
    void lasOtrasCapacidadesSiguenIgual() throws Exception {
        JsonNode admin = leer(pedir(sesionDe(usuario("ADMIN", "ACTIVO")))).get("capabilities");
        JsonNode aprendiz = leer(pedir(sesionDe(usuario("APRENDIZ", "ACTIVO")))).get("capabilities");

        assertThat(admin.get("canAdminister").asBoolean()).isTrue();
        assertThat(aprendiz.get("canAdminister").asBoolean()).isFalse();
        assertThat(aprendiz.get("programRequired").asBoolean()).isTrue();
    }

    @ParameterizedTest(name = "{0} suspendido recibe 403 y no lee ninguna capacidad")
    @CsvSource({"LIDER_MENTORES", "ADMIN", "ALQUIMISTA", "SOPORTE_TI", "MENTOR", "APRENDIZ"})
    void unaCuentaSuspendidaRecibe403(String rol) throws Exception {
        UUID persona = usuario(rol, "ACTIVO");
        String sesion = sesionDe(persona);
        jdbcClient.sql("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = :id").param("id", persona).update();

        assertThat(pedir(sesion).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("sin sesión no hay capacidades")
    void sinSesionNoHayNada() throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + CONTEXTO)).GET().build();

        assertThat(http.send(pedido, HttpResponse.BodyHandlers.ofString()).statusCode()).isIn(401, 403);
    }

    private JsonNode leer(HttpResponse<String> respuesta) throws Exception {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String sesion) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + CONTEXTO))
                .header("X-Auth-Token", sesion).GET().build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
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

    private UUID usuario(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario),
                                CAST(:estado AS renaser.estado_usuario))
                        """)
                .param("id", id).param("email", id + "@renaser.test").param("nombre", "Fixture " + rol)
                .param("rol", rol).param("estado", estado).update();
        usuarios.add(id);
        return id;
    }
}

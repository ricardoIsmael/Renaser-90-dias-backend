package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La foto del chat de soporte (D-205) de punta a punta sobre el Tomcat real: sesión de verdad por
 * {@code X-Auth-Token}, Spring Security delante, Postgres con la conversación y el dibujo de verdad.
 * Lo que el test del controller no ve: que sin sesión es 403, que Spring Security no pisa el
 * {@code Cache-Control} de la foto y que el JPEG sale entero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FotoDelSoporteIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> conversaciones = new ArrayList<>();

    private UUID ana;
    private UUID kelin;
    private UUID luis;
    private UUID soporte;
    private UUID directa;

    @BeforeEach
    void seed() {
        ana = usuario("APRENDIZ", "Ana Pérez");
        kelin = usuario("ADMIN", "Kelin Rojas");
        luis = usuario("APRENDIZ", "Luis Soto");
        soporte = conversacion("SOPORTE", "soporte:" + ana, "Ana – Formación Renaser");
        participa(soporte, ana);
        participa(soporte, kelin);
        directa = conversacion("DIRECTA", ana + "_" + luis, null);
        participa(directa, ana);
        participa(directa, luis);
    }

    @AfterEach
    void limpiar() {
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("la aprendiz y el staff reciben la tarjeta (JPEG entero), privada por un día y con ETag; con ese ETag, 304")
    void laTarjetaDelSoporte() throws Exception {
        HttpResponse<byte[]> deAna = pedir(soporte, sesionDe(ana), null);
        HttpResponse<byte[]> deKelin = pedir(soporte, sesionDe(kelin), null);

        assertThat(deAna.statusCode()).isEqualTo(200);
        assertThat(deAna.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(deAna.headers().firstValue("Cache-Control")).hasValueSatisfying(valor -> assertThat(valor)
                .contains("max-age=86400").contains("private").doesNotContain("no-store"));
        assertThat(deAna.body()).startsWith((byte) 0xFF, (byte) 0xD8).hasSizeGreaterThan(20_000);
        String etag = deAna.headers().firstValue("ETag").orElseThrow();
        assertThat(deKelin.statusCode()).isEqualTo(200);
        assertThat(deKelin.headers().firstValue("ETag")).as("la misma tarjeta, la de Ana").hasValue(etag);

        HttpResponse<byte[]> revalidada = pedir(soporte, sesionDe(ana), etag);
        assertThat(revalidada.statusCode()).isEqualTo(304);
        assertThat(revalidada.body()).isEmpty();
    }

    @Test
    @DisplayName("403 a quien no participa del soporte y sin sesión; 404 si la conversación no es un soporte o no existe")
    void losRechazos() throws Exception {
        assertThat(pedir(soporte, sesionDe(luis), null).statusCode()).as("no participa").isEqualTo(403);
        assertThat(pedir(soporte, null, null).statusCode()).as("sin sesión").isEqualTo(403);
        assertThat(pedir(directa, sesionDe(ana), null).statusCode()).as("un 1 a 1 no tiene foto propia").isEqualTo(404);
        assertThat(pedir(UUID.randomUUID(), sesionDe(ana), null).statusCode()).as("no existe").isEqualTo(404);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private HttpResponse<byte[]> pedir(UUID conversacion, String sesion, String etag) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(
                URI.create("http://localhost:" + puerto + "/api/v1/chat/conversations/" + conversacion + "/foto"));
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        if (etag != null) {
            pedido.header("If-None-Match", etag);
        }
        return http.send(pedido.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
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

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        usuarios.add(id);
        return id;
    }

    private UUID conversacion(String tipo, String clave, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre)
                VALUES (?, CAST(? AS renaser.tipo_conversacion), ?, ?)
                """, id, tipo, clave, nombre);
        conversaciones.add(id);
        return id;
    }

    private void participa(UUID conversacion, UUID usuario) {
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                conversacion, usuario);
    }
}

package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La foto propia de un grupo de punta a punta (D-212): sesión de verdad por {@code X-Auth-Token}, Spring
 * Security y el interceptor de permisos delante, Postgres con las columnas de V75, la foto preparada de
 * verdad (Java2D) y el chat que la sirve. El almacenamiento es uno en memoria: el de las pruebas (noop) no
 * guarda nada, y lo que se prueba es que el backend sube, sirve y borra el objeto correcto.
 *
 * <p>Lo que los tests de controller no ven: que sin sesión es 403, que el tope de 2 MB del multipart sale
 * como 413, que la ruta con {@code ?v=} llega a la lista de chats y sirve la foto, y el 403 del
 * interceptor a un aprendiz con la sesión real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FotoDelGrupoIT.AlmacenamientoDePrueba.class})
class FotoDelGrupoIT {

    /** Guarda de verdad, en memoria: el de las pruebas (noop) no guarda nada que después se pueda servir. */
    @TestConfiguration
    static class AlmacenamientoDePrueba {
        @Bean
        @Primary
        AlmacenamientoEnMemoria almacenamientoEnMemoria() {
            return new AlmacenamientoEnMemoria();
        }
    }

    static class AlmacenamientoEnMemoria implements AlmacenamientoPort {
        final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

        @Override
        public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
            return URI.create("memoria:/" + ruta);
        }

        @Override
        public URI firmarLectura(String ruta, Duration validez) {
            return URI.create("memoria:/" + ruta);
        }

        @Override
        public URI urlPublica(String ruta) {
            return URI.create("memoria:/" + ruta);
        }

        @Override
        public void subir(String ruta, byte[] contenido, String tipoContenido) {
            objetos.put(ruta, contenido);
        }

        @Override
        public void borrar(String ruta) {
            objetos.remove(ruta);
        }

        @Override
        public Optional<byte[]> leer(String ruta) {
            return Optional.ofNullable(objetos.get(ruta));
        }
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AlmacenamientoEnMemoria almacenamiento;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();

    private UUID admin;
    private UUID alquimista;
    private UUID ricardo;
    private UUID otroMentor;
    private UUID ana;
    private UUID cohorte;
    private UUID fenix;
    private UUID aurora;
    private UUID chatDeFenix;

    @BeforeEach
    void seed() {
        almacenamiento.objetos.clear();
        admin = usuario("ADMIN", "Kelin Admin");
        alquimista = usuario("ALQUIMISTA", "Alquimista");
        ricardo = mentor("Ricardo Palomino");
        otroMentor = mentor("Otro Mentor");
        ana = usuario("APRENDIZ", "Ana Pérez");
        cohorte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte fotos', CURRENT_DATE - 10)",
                cohorte);
        fenix = grupo("Fenix", ricardo);
        aurora = grupo("Aurora", otroMentor);
        asignar(fenix, ricardo, "MENTOR");
        asignar(fenix, ana, "APRENDIZ");
        asignar(aurora, otroMentor, "MENTOR");
        chatDeFenix = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.conversaciones (id, tipo, celula_id, nombre) VALUES (?, 'CELULA', ?, 'Fenix')",
                chatDeFenix, fenix);
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                chatDeFenix, ana);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", chatDeFenix);
        for (UUID grupo : List.of(fenix, aurora)) {
            jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", grupo);
            jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        }
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("el mentor del grupo la cambia; la lista de chats trae la ruta con ?v= y esa ruta sirve la foto preparada")
    void elMentorLaCambiaYLaVenSusIntegrantes() throws Exception {
        HttpResponse<byte[]> cambio = subir(fenix, sesionDe(ricardo), "foto", "image/png", png(300, 200));

        assertThat(cambio.statusCode()).isEqualTo(200);
        String cambiadaEn = json.readTree(cambio.body()).get("photoChangedAt").asText();
        String ruta = jdbcTemplate.queryForObject("SELECT foto_ruta FROM renaser.celulas WHERE id = ?", String.class, fenix);
        assertThat(ruta).startsWith("grupos/" + fenix + "/foto-").endsWith(".jpg");
        BufferedImage guardada = ImageIO.read(new ByteArrayInputStream(almacenamiento.objetos.get(ruta)));
        assertThat(guardada.getWidth()).as("la preparó el servidor: cuadrada de 512").isEqualTo(512);
        assertThat(guardada.getHeight()).isEqualTo(512);

        String photoPath = conversacion(sesionDe(ana), chatDeFenix).get("photoPath").asText();
        assertThat(photoPath).isEqualTo("/api/v1/chat/conversations/" + chatDeFenix + "/foto?v="
                + java.time.Instant.parse(cambiadaEn).toEpochMilli());

        HttpResponse<byte[]> foto = pedir("GET", photoPath, sesionDe(ana));
        assertThat(foto.statusCode()).isEqualTo(200);
        assertThat(foto.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(foto.headers().firstValue("Cache-Control")).hasValueSatisfying(valor -> assertThat(valor)
                .contains("max-age=86400").contains("private"));
        assertThat(foto.body()).isEqualTo(almacenamiento.objetos.get(ruta));
        String etag = foto.headers().firstValue("ETag").orElseThrow();
        assertThat(pedirConEtag(photoPath, sesionDe(ana), etag).statusCode()).isEqualTo(304);
    }

    @Test
    @DisplayName("el ADMIN la cambia (la anterior se borra) y vuelve a la de Renaser: sin ruta en la lista y 404 en la foto")
    void elAdminLaCambiaYVuelveALaDeRenaser() throws Exception {
        String sesion = sesionDe(admin);
        assertThat(subir(fenix, sesion, "foto", "image/png", png(100, 100)).statusCode()).isEqualTo(200);
        String primera = jdbcTemplate.queryForObject("SELECT foto_ruta FROM renaser.celulas WHERE id = ?", String.class, fenix);
        Thread.sleep(5);
        assertThat(subir(fenix, sesion, "foto", "image/png", png(120, 120)).statusCode()).isEqualTo(200);
        String segunda = jdbcTemplate.queryForObject("SELECT foto_ruta FROM renaser.celulas WHERE id = ?", String.class, fenix);
        assertThat(segunda).isNotEqualTo(primera);
        assertThat(almacenamiento.objetos).as("la anterior se borró").containsOnlyKeys(segunda);
        assertThat(json.readTree(pedir("GET", "/api/v1/admin/cells/" + fenix + "/photo", sesion).body())
                .get("photoChangedAt").isNull()).isFalse();

        assertThat(pedir("DELETE", "/api/v1/admin/cells/" + fenix + "/photo", sesion).statusCode()).isEqualTo(204);

        assertThat(jdbcTemplate.queryForMap("SELECT foto_ruta, foto_cambiada_en FROM renaser.celulas WHERE id = ?", fenix))
                .containsEntry("foto_ruta", null).containsEntry("foto_cambiada_en", null);
        assertThat(almacenamiento.objetos).isEmpty();
        JsonNode sinFoto = conversacion(sesionDe(ana), chatDeFenix).get("photoPath");
        assertThat(sinFoto == null || sinFoto.isNull()).as("sin photoPath").isTrue();
        assertThat(pedir("GET", "/api/v1/chat/conversations/" + chatDeFenix + "/foto", sesionDe(ana)).statusCode())
                .as("la app muestra la tarjeta que trae").isEqualTo(404);
    }

    @Test
    @DisplayName("autorización negativa: aprendiz del grupo, mentor de otro grupo, Alquimista, ADMIN suspendido y sin sesión: 403")
    void nadieMasLaCambia() throws Exception {
        UUID adminSuspendido = usuario("ADMIN", "Admin suspendido");
        String sesionSuspendida = sesionDe(adminSuspendido);
        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", adminSuspendido);

        assertThat(subir(fenix, sesionDe(ana), "foto", "image/png", png(50, 50)).statusCode()).as("aprendiz").isEqualTo(403);
        assertThat(subir(fenix, sesionDe(otroMentor), "foto", "image/png", png(50, 50)).statusCode()).as("mentor de Aurora")
                .isEqualTo(403);
        assertThat(subir(fenix, sesionDe(alquimista), "foto", "image/png", png(50, 50)).statusCode()).as("Alquimista")
                .isEqualTo(403);
        assertThat(subir(fenix, sesionSuspendida, "foto", "image/png", png(50, 50)).statusCode()).as("suspendido")
                .isEqualTo(403);
        assertThat(subir(fenix, null, "foto", "image/png", png(50, 50)).statusCode()).as("sin sesión").isEqualTo(403);
        assertThat(pedir("DELETE", "/api/v1/admin/cells/" + fenix + "/photo", sesionDe(otroMentor)).statusCode())
                .isEqualTo(403);
        assertThat(almacenamiento.objetos).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT foto_ruta FROM renaser.celulas WHERE id = ?", String.class, fenix))
                .isNull();
    }

    @Test
    @DisplayName("tipo y peso acotados: algo que no es imagen o un GIF son 400, más de 2 MB es 413; sin la parte, 400; sin grupo, 404")
    void loQueNoSeAcepta() throws Exception {
        String sesion = sesionDe(admin);

        assertThat(subir(fenix, sesion, "foto", "image/jpeg", "no soy una foto".getBytes(StandardCharsets.UTF_8))
                .statusCode()).isEqualTo(400);
        assertThat(subir(fenix, sesion, "foto", "image/gif", png(10, 10)).statusCode()).isEqualTo(400);
        HttpResponse<byte[]> pesada = subir(fenix, sesion, "foto", "image/jpeg", new byte[2 * 1024 * 1024 + 10]);
        assertThat(pesada.statusCode()).isEqualTo(413);
        assertThat(json.readTree(pesada.body()).get("message").asText()).contains("2 MB");
        assertThat(subir(fenix, sesion, "otra", "image/png", png(10, 10)).statusCode()).isEqualTo(400);
        assertThat(subir(UUID.randomUUID(), sesion, "foto", "image/png", png(10, 10)).statusCode()).isEqualTo(404);
        assertThat(almacenamiento.objetos).isEmpty();
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private JsonNode conversacion(String sesion, UUID id) throws Exception {
        HttpResponse<byte[]> respuesta = pedir("GET", "/api/v1/chat/conversations", sesion);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        for (JsonNode resumen : json.readTree(respuesta.body())) {
            if (resumen.get("conversation").get("id").asText().equals(id.toString())) {
                return resumen.get("conversation");
            }
        }
        throw new AssertionError("La conversación no está en la lista: " + id);
    }

    private HttpResponse<byte[]> subir(UUID grupo, String sesion, String parte, String tipo, byte[] contenido)
            throws Exception {
        String limite = "----renaser" + UUID.randomUUID();
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        cuerpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"" + parte + "\"; filename=\"foto\"\r\n"
                + "Content-Type: " + tipo + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.write(contenido);
        cuerpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                        + "/api/v1/admin/cells/" + grupo + "/photo"))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()));
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> pedir(String metodo, String ruta, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, HttpRequest.BodyPublishers.noBody());
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> pedirConEtag(String ruta, String sesion, String etag) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", sesion).header("If-None-Match", etag).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static byte[] png(int ancho, int alto) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", salida);
        return salida.toByteArray();
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

    private UUID mentor(String nombre) {
        UUID id = usuario("MENTOR", nombre);
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id);
        return id;
    }

    private UUID grupo(String nombre, UUID mentorId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20, ?)
                """, id, nombre, cohorte, mentorId);
        return id;
    }

    private void asignar(UUID grupo, UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 hour', 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, "prueba|" + id);
    }
}

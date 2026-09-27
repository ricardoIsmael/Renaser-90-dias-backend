package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.application.ports.out.AlmacenamientoEnMemoria;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La portada nueva de punta a punta (D-210), con un almacenamiento en memoria en lugar del de marcador:
 * subirla, verla antes de usarla, confirmarla y que la foto del chat de soporte (D-205) salga con ella
 * —con otro {@code ETag}: la caché no sirve la tarjeta vieja (E-351)—, y volver a la original.
 * El PUT del teléfono al almacenamiento se simula guardando los bytes en la ruta firmada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, PortadaDeBienvenidaIT.AlmacenamientoDePrueba.class})
class PortadaDeBienvenidaIT {

    private static final String BASE = "/api/v1/admin/bienvenida";
    private static final Color CELESTE = new Color(0xCF, 0xE8, 0xF5);

    @TestConfiguration(proxyBeanMethods = false)
    static class AlmacenamientoDePrueba {
        @Bean
        @Primary
        AlmacenamientoEnMemoria almacenamientoEnMemoria() {
            return new AlmacenamientoEnMemoria();
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
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<UUID> usuarios = new ArrayList<>();
    private UUID kelin;
    private UUID ana;
    private UUID soporte;

    @BeforeEach
    void seed() {
        kelin = usuario("ADMIN", "Kelin Rojas");
        ana = usuario("APRENDIZ", "Ana Pérez");
        soporte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre) VALUES (?, 'SOPORTE', ?, ?)",
                soporte, "soporte:" + ana, "Ana – Formación Renaser");
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                soporte, ana);
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                soporte, kelin);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.cambios_bienvenida");
        jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", soporte);
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("subir, ver, usar: la foto del soporte sale con la portada nueva y otro ETag; al volver, la original")
    void cambiarLaPortada() throws Exception {
        String sesionKelin = sesionDe(kelin);
        HttpResponse<byte[]> antes = foto(sesionDe(ana), null);
        assertThat(antes.statusCode()).isEqualTo(200);
        String etagOriginal = antes.headers().firstValue("ETag").orElseThrow();

        String ruta = subir(sesionKelin, imagen(CELESTE));
        HttpResponse<byte[]> muestra = tarjeta(sesionKelin, "María José", ruta);
        assertThat(muestra.statusCode()).isEqualTo(200);
        assertThat(parecido(ImageIO.read(new ByteArrayInputStream(muestra.body())).getRGB(100, 100), CELESTE))
                .as("la vista previa ya es sobre la candidata").isTrue();
        assertThat(foto(sesionDe(ana), etagOriginal).statusCode()).as("todavía no se usó: la foto no cambió")
                .isEqualTo(304);

        JsonNode usada = json.readTree(pedir("POST", BASE + "/portada/confirm", sesionKelin, Map.of("ruta", ruta)).body());
        assertThat(usada.path("portada").path("cambiada").asBoolean()).isTrue();
        assertThat(usada.path("portada").path("ultimoCambio").path("por").asString()).isEqualTo("Kelin Rojas");
        assertThat(jdbcTemplate.queryForObject("SELECT portada_ruta FROM renaser.cambios_bienvenida WHERE pieza = 'PORTADA'",
                String.class)).isEqualTo(ruta);

        HttpResponse<byte[]> despues = foto(sesionDe(ana), etagOriginal);
        assertThat(despues.statusCode()).as("con el ETag viejo ya no es 304: la caché no sirve la vieja").isEqualTo(200);
        assertThat(despues.headers().firstValue("ETag")).isPresent().get().isNotEqualTo(etagOriginal);
        assertThat(parecido(ImageIO.read(new ByteArrayInputStream(despues.body())).getRGB(100, 100), CELESTE)).isTrue();

        assertThat(pedir("DELETE", BASE + "/portada", sesionKelin, null).statusCode()).isEqualTo(200);
        assertThat(foto(sesionDe(ana), null).headers().firstValue("ETag")).hasValue(etagOriginal);
    }

    @Test
    @DisplayName("una portada donde el nombre no se leería: 400 con el motivo en la vista previa y al confirmar, y no queda nada")
    void unaOscuraNoSeUsa() throws Exception {
        String sesionKelin = sesionDe(kelin);
        String ruta = subir(sesionKelin, imagen(new Color(0x20, 0x20, 0x20)));

        HttpResponse<byte[]> muestra = tarjeta(sesionKelin, "Ana", ruta);
        assertThat(muestra.statusCode()).isEqualTo(400);
        assertThat(json.readTree(muestra.body()).path("message").asString()).contains("El nombre no se leería");
        HttpResponse<String> confirmada = pedir("POST", BASE + "/portada/confirm", sesionKelin, Map.of("ruta", ruta));
        assertThat(confirmada.statusCode()).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.cambios_bienvenida", Integer.class)).isZero();
    }

    // ── Pasos y semilla ─────────────────────────────────────────────────────

    /** Paso 1 y 2: la URL de subida y el PUT del teléfono (acá, guardar los bytes en esa ruta). */
    private String subir(String sesion, byte[] png) throws Exception {
        JsonNode subida = json.readTree(pedir("POST", BASE + "/portada/upload-url", sesion,
                Map.of("contentType", "image/png")).body());
        String ruta = subida.path("ruta").asString();
        assertThat(ruta).startsWith("bienvenida/portadas/");
        assertThat(subida.path("url").asString()).startsWith("https://");
        almacenamiento.guardar(ruta, png);
        return ruta;
    }

    private HttpResponse<byte[]> tarjeta(String sesion, String nombre, String portada) throws Exception {
        String consulta = "?nombre=" + URLEncoder.encode(nombre, StandardCharsets.UTF_8) + "&portada="
                + URLEncoder.encode(portada, StandardCharsets.UTF_8);
        return http.send(HttpRequest.newBuilder(URI.create(url(BASE + "/tarjeta" + consulta)))
                .header("X-Auth-Token", sesion).header("Accept", "image/jpeg, application/json").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> foto(String sesion, String etag) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url("/api/v1/chat/conversations/" + soporte + "/foto")))
                .header("X-Auth-Token", sesion);
        if (etag != null) {
            pedido.header("If-None-Match", etag);
        }
        return http.send(pedido.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url(ruta))).header("X-Auth-Token", sesion);
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] imagen(Color color) throws Exception {
        BufferedImage imagen = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 1200, 1200);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        return salida.toByteArray();
    }

    private static boolean parecido(int rgb, Color esperado) {
        return Math.abs(((rgb >> 16) & 0xFF) - esperado.getRed()) <= 12
                && Math.abs(((rgb >> 8) & 0xFF) - esperado.getGreen()) <= 12
                && Math.abs((rgb & 0xFF) - esperado.getBlue()) <= 12;
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
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

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        usuarios.add(id);
        return id;
    }
}

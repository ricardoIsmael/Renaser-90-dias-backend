package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnSoporteUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La bienvenida editable (D-210) de punta a punta: Tomcat real con sesión por {@code X-Auth-Token},
 * Spring Security e interceptor delante, Postgres con V73, y la bienvenida del soporte de verdad
 * (interruptor PRENDIDO). En las pruebas el almacenamiento es de marcador: la portada no se puede cambiar
 * (409) y la bienvenida sale sin tarjeta, solo con el mensaje formal (G-5), que es el que se cambia acá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "renaser.chat.bienvenida.activa=true")
@Import(TestcontainersConfiguration.class)
class BienvenidaEditableIT {

    private static final String BASE = "/api/v1/admin/bienvenida";
    private static final String FORMAL_NUEVO = "Hola, {nombre}. Te damos la bienvenida oficial: tu ingreso está confirmado.";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DarBienvenidaEnSoporteUseCase darBienvenida;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> conversaciones = new ArrayList<>();

    @AfterEach
    void limpiar() {
        // La bitácora es append-only para el código, no para la prueba: otras pruebas esperan los textos del repo.
        jdbcTemplate.update("DELETE FROM renaser.cambios_bienvenida");
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("un texto guardado por ADMIN se usa en la próxima bienvenida, queda quién y cuándo, y al volver al original sale el del repo")
    void elTextoGuardadoSaleEnLaProximaBienvenida() throws Exception {
        UUID kelin = usuario("ADMIN", "ACTIVO", "Kelin Rojas");
        String formalDelRepo = texto(pedir("GET", BASE, sesionDe(kelin), null), "SOPORTE_FORMAL").path("original").asString();

        HttpResponse<String> guardado = pedir("PUT", BASE + "/textos/SOPORTE_FORMAL", sesionDe(kelin),
                Map.of("texto", FORMAL_NUEVO));

        assertThat(guardado.statusCode()).isEqualTo(200);
        JsonNode formal = texto(guardado, "SOPORTE_FORMAL");
        assertThat(formal.path("texto").asString()).isEqualTo(FORMAL_NUEVO);
        assertThat(formal.path("cambiado").asBoolean()).isTrue();
        assertThat(formal.path("ultimoCambio").path("por").asString()).isEqualTo("Kelin Rojas");
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT pieza, texto, portada_ruta, cambiado_por, cambiado_en FROM renaser.cambios_bienvenida");
        assertThat(fila).containsEntry("pieza", "SOPORTE_FORMAL").containsEntry("texto", FORMAL_NUEVO)
                .containsEntry("cambiado_por", kelin).containsEntry("portada_ruta", null);
        assertThat(fila.get("cambiado_en")).isNotNull();

        assertThat(formalQueRecibe("Ana Pérez")).isEqualTo(FORMAL_NUEVO.replace("{nombre}", "Ana"));

        HttpResponse<String> vuelta = pedir("DELETE", BASE + "/textos/SOPORTE_FORMAL", sesionDe(kelin), null);
        assertThat(vuelta.statusCode()).isEqualTo(200);
        assertThat(texto(vuelta, "SOPORTE_FORMAL").path("cambiado").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.cambios_bienvenida WHERE texto IS NULL",
                Integer.class)).as("la vuelta al original también queda en la bitácora").isEqualTo(1);

        assertThat(formalQueRecibe("Luis Soto")).isEqualTo(formalDelRepo.replace("{nombre}", "Luis"));
    }

    @Test
    @DisplayName("ALCHEMIST también puede; un texto sin {nombre} es 400 con el motivo y no queda nada")
    void alquimistaYTextoSinNombre() throws Exception {
        UUID alquimista = usuario("ALQUIMISTA", "ACTIVO", "Rosa Alquimia");

        HttpResponse<String> sinNombre = pedir("PUT", BASE + "/textos/GRUPO", sesionDe(alquimista),
                Map.of("texto", "¡Bienvenida al grupo! Te acompaña {mentor}."));

        assertThat(sinNombre.statusCode()).isEqualTo(400);
        assertThat(json.readTree(sinNombre.body()).path("message").asString()).contains("{nombre}");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.cambios_bienvenida", Integer.class)).isZero();
        assertThat(pedir("GET", BASE, sesionDe(alquimista), null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("autorización negativa: MENTOR, TRAINEE, un ADMIN SUSPENDIDO y sin sesión reciben 403, y no queda nada")
    void losDemasNo() throws Exception {
        List<String> rechazados = new ArrayList<>();
        rechazados.add(sesionDe(usuario("MENTOR", "ACTIVO", "Carlos Mentor")));
        rechazados.add(sesionDe(usuario("LIDER_MENTORES", "ACTIVO", "Lía Líder")));
        rechazados.add(sesionDe(usuario("APRENDIZ", "ACTIVO", "Ana Pérez")));
        rechazados.add(sesionDe(usuario("ADMIN", "SUSPENDIDO", "Kelin Suspendida")));
        rechazados.add(null);

        for (String sesion : rechazados) {
            assertThat(pedir("GET", BASE, sesion, null).statusCode()).isEqualTo(403);
            assertThat(pedir("PUT", BASE + "/textos/SOPORTE_FORMAL", sesion, Map.of("texto", FORMAL_NUEVO)).statusCode())
                    .isEqualTo(403);
            assertThat(pedir("POST", BASE + "/portada/upload-url", sesion, Map.of("contentType", "image/jpeg"))
                    .statusCode()).isEqualTo(403);
            assertThat(pedir("GET", BASE + "/tarjeta?nombre=Ana", sesion, null).statusCode()).isEqualTo(403);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.cambios_bienvenida", Integer.class)).isZero();
    }

    @Test
    @DisplayName("portada sin almacenamiento de verdad (local): se avisa, la URL es de marcador, confirmar es 409; la tarjeta de muestra sale")
    void portadaSinAlmacenamiento() throws Exception {
        String sesion = sesionDe(usuario("ADMIN", "ACTIVO", "Kelin Rojas"));

        assertThat(json.readTree(pedir("GET", BASE, sesion, null).body()).path("portada").path("sePuedeCambiar").asBoolean())
                .isFalse();
        JsonNode subida = json.readTree(pedir("POST", BASE + "/portada/upload-url", sesion,
                Map.of("contentType", "image/jpeg")).body());
        assertThat(subida.path("url").asString()).startsWith("about:blank#pendiente-s3/bienvenida/portadas/");
        assertThat(pedir("POST", BASE + "/portada/confirm", sesion, Map.of("ruta", subida.path("ruta").asString()))
                .statusCode()).isEqualTo(409);

        HttpResponse<byte[]> tarjeta = http.send(HttpRequest.newBuilder(URI.create(url(BASE + "/tarjeta?nombre=Mar%C3%ADa")))
                .header("X-Auth-Token", sesion).header("Accept", "image/jpeg, application/json").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(tarjeta.statusCode()).isEqualTo(200);
        assertThat(tarjeta.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(tarjeta.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(tarjeta.body()).startsWith((byte) 0xFF, (byte) 0xD8).hasSizeGreaterThan(20_000);
    }

    @Test
    @DisplayName("V73: la base exige la forma de cada fila, y borrar la cuenta de quien cambió deja el cambio sin autor")
    void laTablaDeV73() {
        UUID kelin = usuario("ADMIN", "ACTIVO", "Kelin Rojas");

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO renaser.cambios_bienvenida (pieza, texto, portada_ruta) "
                + "VALUES ('SOPORTE_FORMAL', 'Hola {nombre}', 'bienvenida/portadas/x')"))
                .as("un texto no trae ruta").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO renaser.cambios_bienvenida (pieza, texto) "
                + "VALUES ('PORTADA', 'x')")).as("la portada no trae texto").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO renaser.cambios_bienvenida (pieza, portada_ruta) "
                + "VALUES ('PORTADA', 'firmas/ana/pacto.png')")).as("solo rutas de portadas")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO renaser.cambios_bienvenida (pieza, texto) VALUES (?, ?)",
                "GRUPO", "x".repeat(1001))).as("hasta 1000 caracteres").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO renaser.cambios_bienvenida (pieza) VALUES ('OTRA')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("INSERT INTO renaser.cambios_bienvenida (pieza, texto, cambiado_por) VALUES ('GRUPO', ?, ?)",
                "¡{nombre}! {mentor}", kelin);
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", kelin);

        assertThat(jdbcTemplate.queryForMap("SELECT texto, cambiado_por FROM renaser.cambios_bienvenida"))
                .containsEntry("texto", "¡{nombre}! {mentor}").containsEntry("cambiado_por", null);
    }

    // ── Bienvenida, pedidos y semilla ───────────────────────────────────────

    /** Nace el soporte de un aprendiz nuevo y recibe la bienvenida: devuelve el texto que le llegó. */
    private String formalQueRecibe(String nombre) {
        UUID aprendiz = usuario("APRENDIZ", "ACTIVO", nombre);
        UUID soporte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre) VALUES (?, 'SOPORTE', ?, ?)",
                soporte, "soporte:" + aprendiz, nombre + " – Formación Renaser");
        conversaciones.add(soporte);

        darBienvenida.darBienvenida(ConversacionId.of(soporte), UserId.of(aprendiz));

        return jdbcTemplate.queryForObject("SELECT texto FROM renaser.mensajes WHERE conversacion_id = ? AND tipo = 'SISTEMA'",
                String.class, soporte);
    }

    private JsonNode texto(HttpResponse<String> respuesta, String clave) throws Exception {
        for (JsonNode texto : json.readTree(respuesta.body()).path("textos")) {
            if (clave.equals(texto.path("clave").asString())) {
                return texto;
            }
        }
        throw new AssertionError("Sin el texto " + clave + " en " + respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url(ruta)))
                .header("Accept", "application/json, image/jpeg");
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String url(String ruta) {
        return "http://localhost:" + puerto + ruta;
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

    private UUID usuario(String rol, String estado, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        usuarios.add(id);
        return id;
    }
}

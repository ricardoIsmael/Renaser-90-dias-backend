package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-252 (2026-10-05) de punta a punta sobre el Tomcat real: sesión de verdad por {@code X-Auth-Token},
 * Spring Security delante y Postgres con las evidencias. Que {@code GET /api/v1/evidence} trae la foto
 * firmada de cada evidencia con foto ({@code fotoUrl}), que la recibe solo quien ya podía ver esa
 * evidencia, y que la respuesta de siempre no cambió para los APK publicados.
 *
 * <p>El almacenamiento de las pruebas es el de marcador ({@code renaser.storage.proveedor=noop}): la URL
 * sale como {@code about:blank#pendiente-s3/<ruta>}. Alcanza para comprobar que se firmó el objeto de esa
 * evidencia y no otro; la firma real (S3) es del SDK y no se prueba acá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FotosDeEvidenciaIT {

    /** Los campos que ya mandaba {@code EvidenciaResponse} antes de D-252: los APK publicados leen estos. */
    private static final Set<String> CAMPOS_DE_SIEMPRE = Set.of("id", "participanteId", "registroHabitoId",
            "rocaDiariaId", "registroEspirituId", "tipo", "contenidoTexto", "timestampExif", "subidaEn", "gpsLat",
            "gpsLng", "esPrincipal", "estadoValidacion", "notasValidacion", "intentosIa", "penalizacionAplicada",
            "publicadaEnMuro");

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();

    private UUID ana;
    private UUID luis;
    private UUID ricardo;
    private UUID kelin;
    private String rutaDeLaFoto;
    private UUID fotoDeAna;
    private UUID textoDeAna;

    @BeforeEach
    void seed() {
        ricardo = usuario("MENTOR", "Ricardo Palomino");
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", ricardo);
        ana = aprendiz("Ana Pérez", ricardo);
        luis = aprendiz("Luis Soto", null);
        kelin = usuario("ADMIN", "Kelin Rojas");
        UUID roca = rocaDeHoy(ana);
        rutaDeLaFoto = "evidencia-rocas/" + ana + "/" + roca + "/" + UUID.randomUUID();
        fotoDeAna = evidencia(ana, roca, "FOTO", rutaDeLaFoto, null);
        textoDeAna = evidencia(ana, roca, "TEXTO", null, "Caminé 30 minutos");
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa, rocas_diarias, evidencias y perfiles_mentor.
        usuarios.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("el dueño recibe la URL firmada de su foto, del objeto de esa evidencia; su evidencia de texto trae null")
    void elDuenoRecibeSuFoto() throws Exception {
        JsonNode evidencias = listar("/api/v1/evidence", sesionDe(ana));

        assertThat(evidencias).hasSize(2);
        assertThat(evidencia(evidencias, fotoDeAna).get("fotoUrl").asText())
                .startsWith("about:blank#pendiente-s3/").endsWith(rutaDeLaFoto);
        assertThat(evidencia(evidencias, textoDeAna).get("fotoUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("compatibilidad: los 17 campos de siempre siguen con el mismo nombre y valor; fotoUrl es el único nuevo")
    void laRespuestaDeSiempreNoCambia() throws Exception {
        JsonNode foto = evidencia(listar("/api/v1/evidence", sesionDe(ana)), fotoDeAna);

        Set<String> campos = new TreeSet<>();
        foto.fieldNames().forEachRemaining(campos::add);
        Set<String> esperados = new TreeSet<>(CAMPOS_DE_SIEMPRE);
        esperados.add("fotoUrl");
        assertThat(campos).isEqualTo(esperados);
        assertThat(foto.get("participanteId").asText()).isEqualTo(ana.toString());
        assertThat(foto.get("tipo").asText()).isEqualTo("FOTO");
        assertThat(foto.get("estadoValidacion").asText()).isEqualTo("VALIDA");
        assertThat(foto.get("contenidoTexto").isNull()).isTrue();
        assertThat(foto.get("publicadaEnMuro").asBoolean()).isFalse();
        assertThat(foto.toString()).as("ni el bucket ni la ruta viajan como campo propio")
                .doesNotContain("renaser-files").doesNotContain("\"rutaStorage\"").doesNotContain("\"bucket\"");
    }

    @Test
    @DisplayName("otro aprendiz: pedir el listado de Ana es 403, y en el suyo no aparece ninguna foto de Ana")
    void otroAprendizNoVeLaFoto() throws Exception {
        HttpResponse<String> ajeno = pedir("/api/v1/evidence?participanteId=" + ana, sesionDe(luis));
        JsonNode propias = listar("/api/v1/evidence", sesionDe(luis));

        assertThat(ajeno.statusCode()).isEqualTo(403);
        assertThat(ajeno.body()).doesNotContain(rutaDeLaFoto);
        assertThat(propias).isEmpty();
    }

    @Test
    @DisplayName("autorización negativa: sin sesión y con la cuenta suspendida, 403 y ninguna URL")
    void sinSesionYSuspendidaSon403() throws Exception {
        String sesionDeAna = sesionDe(ana);
        HttpResponse<String> sinSesion = pedir("/api/v1/evidence", null);
        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", ana);
        HttpResponse<String> suspendida = pedir("/api/v1/evidence", sesionDeAna);

        assertThat(sinSesion.statusCode()).isEqualTo(403);
        assertThat(suspendida.statusCode()).isEqualTo(403);
        assertThat(sinSesion.body() + suspendida.body()).doesNotContain(rutaDeLaFoto);
    }

    @Test
    @DisplayName("el mentor asignado recibe la foto de su aprendiz; un mentor no asignado (Luis no es su aprendiz), 403")
    void elMentorAsignadoVeLaFoto() throws Exception {
        JsonNode deSuAprendiz = listar("/api/v1/evidence?participanteId=" + ana, sesionDe(ricardo));
        HttpResponse<String> deOtro = pedir("/api/v1/evidence?participanteId=" + luis, sesionDe(ricardo));

        assertThat(evidencia(deSuAprendiz, fotoDeAna).get("fotoUrl").asText()).endsWith(rutaDeLaFoto);
        assertThat(deOtro.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("solo el listado de la app firma: el detalle y el listado del panel admin traen fotoUrl null")
    void elDetalleYElPanelNoFirman() throws Exception {
        HttpResponse<String> detalle = pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(ana));
        JsonNode delPanel = listar("/api/v1/admin/evidence?participanteId=" + ana, sesionDe(kelin));

        assertThat(detalle.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detalle.body()).get("fotoUrl").isNull()).isTrue();
        assertThat(evidencia(delPanel, fotoDeAna).get("fotoUrl").isNull()).isTrue();
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private JsonNode listar(String ruta, String sesion) throws Exception {
        HttpResponse<String> respuesta = pedir(ruta, sesion);
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        return json.readTree(respuesta.body()).get("evidencias");
    }

    private static JsonNode evidencia(JsonNode evidencias, UUID id) {
        for (JsonNode evidencia : evidencias) {
            if (evidencia.get("id").asText().equals(id.toString())) {
                return evidencia;
            }
        }
        throw new AssertionError("La evidencia " + id + " no está en el listado: " + evidencias);
    }

    private HttpResponse<String> pedir(String ruta, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.GET().build(), HttpResponse.BodyHandlers.ofString());
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

    private UUID aprendiz(String nombre, UUID mentor) {
        UUID id = usuario("APRENDIZ", nombre);
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, mentor_id) VALUES (?, 20, ?)",
                id, mentor);
        return id;
    }

    private UUID rocaDeHoy(UUID participante) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                VALUES (?, ?, CURRENT_DATE, 1, 'Caminar', CAST('VERDE' AS renaser.color_pareto), 5,
                        CAST('CUERPO' AS renaser.eje_objetivo))
                """, id, participante);
        return id;
    }

    private UUID evidencia(UUID participante, UUID roca, String tipo, String ruta, String texto) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.evidencias (id, participante_id, roca_diaria_id, tipo, bucket, ruta_storage,
                                                contenido_texto, estado_validacion)
                VALUES (?, ?, ?, CAST(? AS renaser.tipo_evidencia), ?, ?, ?, 'VALIDA')
                """, id, participante, roca, tipo, ruta == null ? null : "renaser-files", ruta, texto);
        return id;
    }
}

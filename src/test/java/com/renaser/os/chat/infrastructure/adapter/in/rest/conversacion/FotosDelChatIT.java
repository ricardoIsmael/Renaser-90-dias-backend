package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las tarjetas con nombre del chat de punta a punta sobre el Tomcat real: sesión de verdad por
 * {@code X-Auth-Token}, Spring Security delante, Postgres con las conversaciones y el grupo, y el
 * dibujo de verdad.
 * <ul>
 *   <li><b>La del soporte</b> (D-205): que sin sesión es 403, que Spring Security no pisa el
 *       {@code Cache-Control} y que el JPEG sale entero.</li>
 *   <li><b>La de cada integrante</b> (D-206): que la ruta que llega a la info del chat —la de
 *       {@code /me/cells} para el mentor y la de {@code /me/cells/{id}/members} para los aprendices,
 *       que arma {@code community} preguntándole a chat— sirve de verdad la tarjeta, y que la regla de
 *       quién la ve es la del chat del grupo.</li>
 * </ul>
 * <blockquote><b>Corregido 2026-09-27 (D-206).</b> Se llamaba {@code FotoDelSoporteIT} y solo probaba
 * la foto del soporte.</blockquote>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FotosDelChatIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> conversaciones = new ArrayList<>();

    private UUID ana;
    private UUID kelin;
    private UUID luis;
    private UUID ricardo;
    private UUID beto;
    private UUID soporte;
    private UUID directa;
    private UUID cohorte;
    private UUID grupo;
    private UUID chatDelGrupo;

    @BeforeEach
    void seed() {
        ana = usuario("APRENDIZ", "Ana Pérez");
        kelin = usuario("ADMIN", "Kelin Rojas");
        luis = usuario("APRENDIZ", "Luis Soto");
        soporte = conversacion("SOPORTE", "soporte:" + ana, null, "Ana – Formación Renaser");
        participa(soporte, ana);
        participa(soporte, kelin);
        directa = conversacion("DIRECTA", ana + "_" + luis, null, null);
        participa(directa, ana);
        participa(directa, luis);
        grupoConMentor();
    }

    /** El grupo «Fénix»: mentor Ricardo, aprendices Ana y Beto; Luis no está. Con su chat de grupo. */
    private void grupoConMentor() {
        ricardo = usuario("MENTOR", "Ricardo Palomino");
        beto = usuario("APRENDIZ", "Beto Díaz");
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", ricardo);
        cohorte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte fotos', CURRENT_DATE - 10)",
                cohorte);
        grupo = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, 'Fenix', ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20, ?)
                """, grupo, cohorte, ricardo);
        chatDelGrupo = conversacion("CELULA", null, grupo, "Fenix");
        asignar(ricardo, "MENTOR");
        asignar(ana, "APRENDIZ");
        asignar(beto, "APRENDIZ");
    }

    @AfterEach
    void limpiar() {
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", grupo);
        jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    // ── La foto del soporte (D-205) ─────────────────────────────────────────

    @Test
    @DisplayName("soporte: la aprendiz y el staff reciben la tarjeta (JPEG entero), privada por un día y con ETag; con ese ETag, 304")
    void laTarjetaDelSoporte() throws Exception {
        HttpResponse<byte[]> deAna = pedir(rutaDelSoporte(soporte), sesionDe(ana), null);
        HttpResponse<byte[]> deKelin = pedir(rutaDelSoporte(soporte), sesionDe(kelin), null);

        assertThat(deAna.statusCode()).isEqualTo(200);
        assertThat(deAna.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(deAna.headers().firstValue("Cache-Control")).hasValueSatisfying(valor -> assertThat(valor)
                .contains("max-age=86400").contains("private").doesNotContain("no-store"));
        assertThat(deAna.body()).startsWith((byte) 0xFF, (byte) 0xD8).hasSizeGreaterThan(20_000);
        String etag = deAna.headers().firstValue("ETag").orElseThrow();
        assertThat(deKelin.statusCode()).isEqualTo(200);
        assertThat(deKelin.headers().firstValue("ETag")).as("la misma tarjeta, la de Ana").hasValue(etag);

        HttpResponse<byte[]> revalidada = pedir(rutaDelSoporte(soporte), sesionDe(ana), etag);
        assertThat(revalidada.statusCode()).isEqualTo(304);
        assertThat(revalidada.body()).isEmpty();
    }

    @Test
    @DisplayName("soporte: 403 a quien no participa y sin sesión; 404 si la conversación no es un soporte o no existe")
    void losRechazosDelSoporte() throws Exception {
        assertThat(pedir(rutaDelSoporte(soporte), sesionDe(luis), null).statusCode()).as("no participa").isEqualTo(403);
        assertThat(pedir(rutaDelSoporte(soporte), null, null).statusCode()).as("sin sesión").isEqualTo(403);
        assertThat(pedir(rutaDelSoporte(directa), sesionDe(ana), null).statusCode()).as("un 1 a 1 no tiene foto propia")
                .isEqualTo(404);
        assertThat(pedir(rutaDelSoporte(UUID.randomUUID()), sesionDe(ana), null).statusCode()).as("no existe")
                .isEqualTo(404);
    }

    // ── La foto de cada integrante (D-206) ──────────────────────────────────

    @Test
    @DisplayName("grupo: /me/cells trae el id y la ruta de la tarjeta del mentor, y esa ruta sirve la tarjeta de Ricardo")
    void laTarjetaDelMentorLlegaALaInfo() throws Exception {
        JsonNode celda = celdaDelGrupo(obtenerJson("/api/v1/me/cells", sesionDe(ana)).get("cells"));

        assertThat(celda.get("mentorId").asText()).isEqualTo(ricardo.toString());
        String ruta = celda.get("mentorPhotoPath").asText();
        assertThat(ruta).isEqualTo(rutaDeIntegrante(chatDelGrupo, ricardo));

        HttpResponse<byte[]> foto = pedir(ruta, sesionDe(ana), null);
        assertThat(foto.statusCode()).isEqualTo(200);
        assertThat(foto.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        assertThat(foto.headers().firstValue("Cache-Control")).hasValueSatisfying(valor -> assertThat(valor)
                .contains("max-age=86400").contains("private"));
        assertThat(foto.body()).startsWith((byte) 0xFF, (byte) 0xD8).hasSizeGreaterThan(20_000);
        String etagDeRicardo = foto.headers().firstValue("ETag").orElseThrow();
        assertThat(pedir(ruta, sesionDe(ana), etagDeRicardo).statusCode()).isEqualTo(304);
        assertThat(pedir(rutaDelSoporte(soporte), sesionDe(ana), null).headers().firstValue("ETag"))
                .as("otra persona, otra tarjeta").isNotEqualTo(Optional.of(etagDeRicardo));
    }

    @Test
    @DisplayName("grupo: /me/cells/{id}/members trae la ruta de cada aprendiz —también de quien subió foto, en el modo "
            + "por defecto— y la ve el mentor y la ven sus compañeros")
    void lasTarjetasDeLosAprendicesLleganALaInfo() throws Exception {
        String fotoDeBeto = "https://s3-renaser90dias.s3.amazonaws.com/avatares/" + beto + ".jpg";
        jdbcTemplate.update("UPDATE renaser.usuarios SET avatar_url = ? WHERE id = ?", fotoDeBeto, beto);

        JsonNode integrantes = obtenerJson("/api/v1/me/cells/" + grupo + "/members", sesionDe(ana)).get("members");

        assertThat(integrantes).hasSize(2);
        for (JsonNode integrante : integrantes) {
            UUID id = UUID.fromString(integrante.get("traineeId").asText());
            String ruta = integrante.get("photoPath").asText();
            assertThat(ruta).as("modo TARJETA: la tarjeta aunque haya foto subida").isEqualTo(rutaDeIntegrante(chatDelGrupo, id));
            assertThat(pedir(ruta, sesionDe(ana), null).statusCode()).as("una compañera").isEqualTo(200);
            assertThat(pedir(ruta, sesionDe(ricardo), null).statusCode()).as("el mentor").isEqualTo(200);
        }
    }

    @Test
    @DisplayName("grupo, autorización negativa: 403 a quien no está en el grupo, a una cuenta suspendida y sin sesión")
    void losRechazosDelGrupoSon403() throws Exception {
        String rutaDeBeto = rutaDeIntegrante(chatDelGrupo, beto);
        String sesionDeBeto = sesionDe(beto);
        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", beto);

        assertThat(pedir(rutaDeBeto, sesionDe(luis), null).statusCode()).as("Luis no está en el grupo").isEqualTo(403);
        assertThat(pedir(rutaDeIntegrante(chatDelGrupo, ana), sesionDeBeto, null).statusCode())
                .as("Beto, suspendido").isEqualTo(403);
        assertThat(pedir(rutaDeBeto, null, null).statusCode()).as("sin sesión").isEqualTo(403);
    }

    @Test
    @DisplayName("404: alguien que no es integrante del grupo, la gente de un 1 a 1 y un chat que no existe; en el soporte, el staff sí")
    void losRechazosDelGrupoSon404() throws Exception {
        assertThat(pedir(rutaDeIntegrante(chatDelGrupo, luis), sesionDe(ana), null).statusCode())
                .as("Luis no es integrante del grupo").isEqualTo(404);
        assertThat(pedir(rutaDeIntegrante(directa, luis), sesionDe(ana), null).statusCode())
                .as("un 1 a 1 no tiene tarjetas de integrantes").isEqualTo(404);
        assertThat(pedir(rutaDeIntegrante(UUID.randomUUID(), ana), sesionDe(ana), null).statusCode())
                .as("no existe").isEqualTo(404);
        assertThat(pedir(rutaDeIntegrante(soporte, kelin), sesionDe(ana), null).statusCode())
                .as("en su soporte, la tarjeta del staff sí").isEqualTo(200);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private static String rutaDelSoporte(UUID conversacion) {
        return "/api/v1/chat/conversations/" + conversacion + "/foto";
    }

    private static String rutaDeIntegrante(UUID conversacion, UUID usuario) {
        return "/api/v1/chat/conversations/" + conversacion + "/miembros/" + usuario + "/foto";
    }

    private JsonNode celdaDelGrupo(JsonNode celdas) {
        for (JsonNode celda : celdas) {
            if (celda.get("cellId").asText().equals(grupo.toString())) {
                return celda;
            }
        }
        throw new AssertionError("El grupo no está en /me/cells: " + celdas);
    }

    private JsonNode obtenerJson(String ruta, String sesion) throws Exception {
        HttpResponse<byte[]> respuesta = pedir(ruta, sesion, null);
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<byte[]> pedir(String ruta, String sesion, String etag) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
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

    private UUID conversacion(String tipo, String clave, UUID celula, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa, celula_id, nombre)
                VALUES (?, CAST(? AS renaser.tipo_conversacion), ?, ?, ?)
                """, id, tipo, clave, celula, nombre);
        conversaciones.add(id);
        return id;
    }

    private void participa(UUID conversacion, UUID usuario) {
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                conversacion, usuario);
    }

    private void asignar(UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 hour', 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, "prueba|" + id);
    }
}

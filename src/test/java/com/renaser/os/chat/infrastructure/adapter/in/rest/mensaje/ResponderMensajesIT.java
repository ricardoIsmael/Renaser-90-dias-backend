package com.renaser.os.chat.infrastructure.adapter.in.rest.mensaje;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Responder a un mensaje de punta a punta (D-251): Tomcat real, sesión por {@code X-Auth-Token}, Spring Security
 * delante y el Postgres con V92 aplicada.
 *
 * <p>Semilla: Ana y Luis tienen un 1 a 1 («el chat»); Ana y Kelin, otro («el otro chat»). Kelin no está en el
 * chat de Ana y Luis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ResponderMensajesIT {

    private static final String NO_ESTA = "El mensaje que quieres responder no está en esta conversación";

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
    private UUID luis;
    private UUID kelin;
    private UUID chat;
    private UUID otroChat;

    @BeforeEach
    void seed() {
        ana = usuario("Ana Pérez");
        luis = usuario("Luis Soto");
        kelin = usuario("Kelin Rojas");
        chat = directa(ana, luis);
        otroChat = directa(ana, kelin);
    }

    @AfterEach
    void limpiar() {
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("responder a un mensaje ajeno y a uno propio: la respuesta de enviar y el listado traen el resumen, con «mine» según quien mira")
    void responderAjenoYPropio() throws Exception {
        String deLuis = enviar(chat, luis, cuerpo("¿Vamos mañana a correr?", null)).get("id").asText();

        JsonNode respuesta = enviar(chat, ana, cuerpo("¡Sí, yo también!", deLuis));
        assertThat(respuesta.get("replyToId").asText()).isEqualTo(deLuis);
        assertThat(respuesta.get("replyToDeleted").asBoolean()).isFalse();
        JsonNode cita = respuesta.get("replyTo");
        assertThat(cita.get("id").asText()).isEqualTo(deLuis);
        assertThat(cita.get("senderName").asText()).isEqualTo("Luis Soto");
        assertThat(cita.get("type").asText()).isEqualTo("TEXT");
        assertThat(cita.get("text").asText()).isEqualTo("¿Vamos mañana a correr?");
        assertThat(cita.has("deletedAt")).as("el APK publicado lo exige presente").isTrue();
        assertThat(cita.get("deletedAt").isNull()).isTrue();
        assertThat(cita.get("mine").asBoolean()).isFalse();

        JsonNode aSiMisma = enviar(chat, ana, cuerpo("Llevo agua", respuesta.get("id").asText()));
        assertThat(aSiMisma.get("replyTo").get("mine").asBoolean()).isTrue();
        assertThat(aSiMisma.get("replyTo").get("senderName").asText()).isEqualTo("Ana Pérez");

        JsonNode paraLuis = mensaje(listar(chat, luis), respuesta.get("id").asText());
        assertThat(paraLuis.get("replyTo").get("mine").asBoolean()).as("Luis ve su propio mensaje citado").isTrue();
        JsonNode paraAna = mensaje(listar(chat, ana), respuesta.get("id").asText());
        assertThat(paraAna.get("replyTo").get("mine").asBoolean()).isFalse();
        assertThat(paraAna.get("replyTo").get("text").asText()).isEqualTo("¿Vamos mañana a correr?");
    }

    @Test
    @DisplayName("citar un mensaje de otra conversación o uno que no existe: 400 con el mismo texto, sin filtrar nada y sin guardar")
    void citaDeOtraConversacion() throws Exception {
        String secreto = enviar(otroChat, kelin, cuerpo("Lo que hablamos de tu familia", null)).get("id").asText();

        HttpResponse<String> deOtroChat = pedir("POST", rutaMensajes(chat), ana, cuerpo("Respondo", secreto));
        HttpResponse<String> inexistente = pedir("POST", rutaMensajes(chat), ana, cuerpo("Respondo", UUID.randomUUID().toString()));

        assertThat(deOtroChat.statusCode()).isEqualTo(400);
        assertThat(json.readTree(deOtroChat.body()).get("message").asText()).isEqualTo(NO_ESTA);
        assertThat(deOtroChat.body()).doesNotContain("familia").doesNotContain(kelin.toString());
        assertThat(inexistente.statusCode()).as("antes de D-251 era 404: se distinguía de otro chat").isEqualTo(400);
        assertThat(json.readTree(inexistente.body()).get("message").asText()).isEqualTo(NO_ESTA);
        assertThat(contarMensajes(chat)).isZero();
    }

    @Test
    @DisplayName("la app vieja manda el cuerpo sin replyToId: 201 igual que antes, con todos los campos de siempre")
    void laAppViejaSinElCampo() throws Exception {
        Map<String, Object> cuerpoViejo = Map.of("type", "TEXT", "text", "hola");

        JsonNode creado = enviar(chat, ana, cuerpoViejo);

        for (String campo : List.of("id", "conversationId", "senderId", "senderName", "senderAvatarUrl", "type", "text",
                "mediaBucket", "mediaPath", "mediaMime", "mediaBytes", "mediaDurationSeconds", "mediaUrl", "hidden",
                "replyToId", "replyTo", "createdAt", "status")) {
            assertThat(creado.has(campo)).as(campo).isTrue();
        }
        assertThat(creado.get("replyToId").isNull()).isTrue();
        assertThat(creado.get("replyTo").isNull()).isTrue();
        assertThat(creado.get("replyToDeleted").asBoolean()).isFalse();
        assertThat(mensaje(listar(chat, luis), creado.get("id").asText()).get("replyTo").isNull()).isTrue();
    }

    @Test
    @DisplayName("autorización negativa: 403 a quien no participa, a una cuenta suspendida con sesión válida y sin sesión")
    void autorizacionNegativa() throws Exception {
        String deLuis = enviar(chat, luis, cuerpo("hola", null)).get("id").asText();
        String sesionDeAna = sesionDe(ana);
        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", ana);

        assertThat(pedir("POST", rutaMensajes(chat), kelin, cuerpo("me meto", deLuis)).statusCode())
                .as("Kelin no está en el chat").isEqualTo(403);
        assertThat(pedirConSesion("POST", rutaMensajes(chat), sesionDeAna, cuerpo("respondo", deLuis)).statusCode())
                .as("Ana, suspendida").isEqualTo(403);
        assertThat(pedirConSesion("POST", rutaMensajes(chat), null, cuerpo("respondo", deLuis)).statusCode())
                .as("sin sesión").isEqualTo(403);
        assertThat(pedir("GET", rutaMensajes(chat), kelin, null).statusCode()).as("ni puede leer").isEqualTo(403);
        assertThat(contarMensajes(chat)).isEqualTo(1);
    }

    @Test
    @DisplayName("se borra la cuenta de Luis: la respuesta de Ana sigue, dice replyToDeleted y la página no se rompe (V92)")
    void citadoBorradoConLaCuenta() throws Exception {
        String deLuis = enviar(chat, luis, cuerpo("¿Vamos mañana?", null)).get("id").asText();
        String deAna = enviar(chat, ana, cuerpo("¡Sí!", deLuis)).get("id").asText();

        // Lo mismo que hace la base al borrar la fila de la persona: la cascada de emisor_id se lleva sus mensajes.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", luis);

        JsonNode respuesta = mensaje(listar(chat, ana), deAna);
        assertThat(respuesta.get("text").asText()).isEqualTo("¡Sí!");
        assertThat(respuesta.get("replyToDeleted").asBoolean()).as("antes de V92 la FK lo dejaba en NULL").isTrue();
        assertThat(respuesta.get("replyTo").isNull()).isTrue();
        assertThat(respuesta.get("replyToId").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT respuesta_a_id FROM renaser.mensajes WHERE id = ?::uuid",
                UUID.class, deAna)).as("el id queda guardado").isEqualTo(UUID.fromString(deLuis));
    }

    @Test
    @DisplayName("un mensaje que su autor borró: la respuesta dice replyToDeleted y ya no se le puede responder")
    void citadoBorradoPorSuAutor() throws Exception {
        String deLuis = enviar(chat, luis, cuerpo("Me equivoqué", null)).get("id").asText();
        String deAna = enviar(chat, ana, cuerpo("¿Qué dijiste?", deLuis)).get("id").asText();
        jdbcTemplate.update("UPDATE renaser.mensajes SET eliminado_en = now() WHERE id = ?::uuid", deLuis);

        JsonNode respuesta = mensaje(listar(chat, ana), deAna);
        HttpResponse<String> otraVez = pedir("POST", rutaMensajes(chat), ana, cuerpo("¿Hola?", deLuis));

        assertThat(respuesta.get("replyToDeleted").asBoolean()).isTrue();
        assertThat(respuesta.get("replyTo").isNull()).isTrue();
        assertThat(otraVez.statusCode()).isEqualTo(400);
        assertThat(json.readTree(otraVez.body()).get("message").asText())
                .isEqualTo("Ese mensaje fue eliminado y ya no se puede responder");
    }

    @Test
    @DisplayName("citar un sticker: el resumen trae IMAGE, image/webp, el texto del sticker y la miniatura firmada")
    void citarUnSticker() throws Exception {
        Map<String, Object> sticker = new HashMap<>();
        sticker.put("type", "IMAGE");
        sticker.put("text", "Sticker Renaser: Muy bien");
        sticker.put("mediaBucket", "chat");
        sticker.put("mediaPath", "chat/" + chat + "/fotos/" + UUID.randomUUID());
        sticker.put("mediaMime", "image/webp");
        String deLuis = enviar(chat, luis, sticker).get("id").asText();

        JsonNode cita = enviar(chat, ana, cuerpo("😂", deLuis)).get("replyTo");

        assertThat(cita.get("type").asText()).isEqualTo("IMAGE");
        assertThat(cita.get("mediaMime").asText()).isEqualTo("image/webp");
        assertThat(cita.get("text").asText()).isEqualTo("Sticker Renaser: Muy bien");
        assertThat(cita.get("mediaUrl").isNull()).isFalse();
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private static Map<String, Object> cuerpo(String texto, String replyToId) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("type", "TEXT");
        cuerpo.put("text", texto);
        if (replyToId != null) {
            cuerpo.put("replyToId", replyToId);
        }
        return cuerpo;
    }

    private static String rutaMensajes(UUID conversacion) {
        return "/api/v1/chat/conversations/" + conversacion + "/messages";
    }

    private JsonNode enviar(UUID conversacion, UUID quien, Map<String, Object> cuerpo) throws Exception {
        HttpResponse<String> respuesta = pedir("POST", rutaMensajes(conversacion), quien, cuerpo);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
        return json.readTree(respuesta.body());
    }

    private JsonNode listar(UUID conversacion, UUID quien) throws Exception {
        HttpResponse<String> respuesta = pedir("GET", rutaMensajes(conversacion), quien, null);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body()).get("messages");
    }

    private static JsonNode mensaje(JsonNode mensajes, String id) {
        for (JsonNode mensaje : mensajes) {
            if (mensaje.get("id").asText().equals(id)) {
                return mensaje;
            }
        }
        throw new AssertionError("No está el mensaje " + id + " en " + mensajes);
    }

    private long contarMensajes(UUID conversacion) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ?", Long.class,
                conversacion);
    }

    private HttpResponse<String> pedir(String metodo, String ruta, UUID quien, Object cuerpo) throws Exception {
        return pedirConSesion(metodo, ruta, sesionDe(quien), cuerpo);
    }

    private HttpResponse<String> pedirConSesion(String metodo, String ruta, String sesion, Object cuerpo)
            throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
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

    private UUID usuario(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST('APRENDIZ' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre);
        usuarios.add(id);
        return id;
    }

    private UUID directa(UUID una, UUID otra) {
        UUID id = UUID.randomUUID();
        String clave = una.toString().compareTo(otra.toString()) < 0 ? una + "_" + otra : otra + "_" + una;
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa)
                VALUES (?, CAST('DIRECTA' AS renaser.tipo_conversacion), ?)
                """, id, clave);
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?), (?, ?)",
                id, una, id, otra);
        conversaciones.add(id);
        return id;
    }
}

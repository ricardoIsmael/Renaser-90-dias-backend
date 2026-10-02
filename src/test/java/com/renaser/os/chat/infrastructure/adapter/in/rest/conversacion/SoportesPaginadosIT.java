package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion.SoportesPorActividadJdbcAdapter;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-249: la sección «Soporte» de Tribu de punta a punta —sesión real, interceptor, caso de uso y Postgres—
 * con 320 aprendices y su chat de soporte, como el padrón de 300 que la pidió.
 *
 * <p>Cubre: recorrer todas las páginas da cada soporte UNA vez y en el orden de la lista completa (último
 * mensaje primero; a igual instante, por id), incluidos empates y soportes sin mensajes; la búsqueda en el
 * servidor sin tildes ni mayúsculas, por nombre y por correo; los contadores de la primera página; y el 403
 * para aprendiz, mentor, líder de mentores y una cuenta ADMIN suspendida. Contra el código anterior el
 * endpoint no existe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SoportesPaginadosIT {

    private static final int APRENDICES = 320;

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> soportes = new ArrayList<>();
    private UUID admin;
    private String marca;

    @BeforeEach
    void sembrar() {
        marca = UUID.randomUUID().toString().substring(0, 8);
        admin = usuario("ADMIN", "ACTIVO", "Admin Soportes");
        for (int i = 0; i < APRENDICES; i++) {
            String nombre = switch (i) {
                case 7 -> "José Peña " + marca;
                case 150 -> "MARÍA ÁLVAREZ " + marca;
                default -> "Aprendiz " + i + " " + marca;
            };
            UUID aprendiz = usuario("APRENDIZ", "ACTIVO", nombre);
            soportes.add(soporteDe(aprendiz, i));
        }
        jdbcTemplate.execute("ANALYZE renaser.mensajes");
    }

    @AfterEach
    void limpiar() {
        soportes.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("D-249: las páginas recorren los 320 soportes sin duplicados ni huecos y en el orden de actividad")
    void paginasSinDuplicadosNiHuecos() throws Exception {
        String sesion = sesionDe(admin);
        List<String> recorridos = new ArrayList<>();
        String cursor = null;
        int paginas = 0;
        do {
            JsonNode pagina = pedir(sesion, "size=25" + (cursor == null ? "" : "&cursor=" + cursor), 200);
            pagina.get("conversations").forEach(fila -> {
                assertThat(fila.at("/conversation/type").asText()).isEqualTo("SUPPORT");
                assertThat(fila.at("/conversation/supportTraineeId").isTextual()).isTrue();
                assertThat(fila.at("/conversation/nombre").asText()).endsWith("Formación Renaser");
                recorridos.add(fila.at("/conversation/id").asText());
            });
            assertThat(pagina.get("conversations").size()).isLessThanOrEqualTo(25);
            if (paginas == 0) {
                assertThat(pagina.get("totalCount").asLong()).isEqualTo(APRENDICES);
            } else {
                assertThat(pagina.get("totalCount").isNull()).as("el total va solo en la primera").isTrue();
            }
            cursor = pagina.get("nextCursor").isNull() ? null : pagina.get("nextCursor").asText();
            assertThat(pagina.get("hasMore").asBoolean()).isEqualTo(cursor != null);
            paginas++;
        } while (cursor != null && paginas < 50);

        assertThat(paginas).isEqualTo(13);
        assertThat(new HashSet<>(recorridos)).as("sin duplicados").hasSize(recorridos.size());
        assertThat(recorridos).as("todos, en el orden de la lista completa").isEqualTo(ordenEsperado());
    }

    @Test
    @DisplayName("D-249: el primero es el del mensaje más reciente, y la primera página cuenta los que tienen no leídos")
    void ordenYContadores() throws Exception {
        UUID ultimo = soportes.get(200);
        jdbcTemplate.update("""
                INSERT INTO renaser.mensajes (conversacion_id, emisor_id, texto, creado_en)
                VALUES (?, ?, 'recién llegado', now() + interval '1 hour')
                """, ultimo, admin);
        // Leyó todo menos tres: esos tres (y los que no tienen mensajes no cuentan) quedan con no leídos.
        jdbcTemplate.update("""
                UPDATE renaser.participantes_conversacion SET ultimo_leido_en = now() + interval '2 hours'
                WHERE usuario_id = ? AND conversacion_id NOT IN (?, ?, ?)
                """, admin, soportes.get(1), soportes.get(2), soportes.get(3));

        JsonNode pagina = pedir(sesionDe(admin), "size=5", 200);

        assertThat(pagina.at("/conversations/0/conversation/id").asText()).isEqualTo(ultimo.toString());
        assertThat(pagina.at("/conversations/0/lastMessage/text").asText()).isEqualTo("recién llegado");
        assertThat(pagina.get("unreadConversations").asLong()).isEqualTo(3);
        assertThat(pagina.get("conversations").size()).isEqualTo(5);
    }

    @Test
    @DisplayName("D-249: la búsqueda va al servidor y no mira tildes ni mayúsculas, por nombre y por correo")
    void busquedaSinTildes() throws Exception {
        String sesion = sesionDe(admin);

        JsonNode jose = pedir(sesion, "q=" + enc("jose pena " + marca), 200);
        assertThat(jose.get("conversations")).hasSize(1);
        assertThat(jose.at("/conversations/0/conversation/nombre").asText()).startsWith("José");
        assertThat(jose.get("totalCount").asLong()).isEqualTo(1);

        JsonNode maria = pedir(sesion, "q=" + enc("Maria alvarez"), 200);
        assertThat(maria.get("conversations")).hasSize(1);
        assertThat(maria.at("/conversations/0/conversation/nombre").asText()).startsWith("MARÍA");

        String correo = jdbcTemplate.queryForObject("SELECT email FROM renaser.usuarios WHERE id = ?", String.class,
                UUID.fromString(maria.at("/conversations/0/conversation/supportTraineeId").asText()));
        JsonNode porCorreo = pedir(sesion, "q=" + enc(correo.toUpperCase().substring(0, 20)), 200);
        assertThat(porCorreo.get("conversations")).hasSize(1);

        JsonNode nadie = pedir(sesion, "q=" + enc("zzz-nadie-" + marca), 200);
        assertThat(nadie.get("conversations")).isEmpty();
        assertThat(nadie.get("totalCount").asLong()).isZero();
        assertThat(nadie.get("hasMore").asBoolean()).isFalse();

        JsonNode comodin = pedir(sesion, "q=" + enc("%"), 200);
        assertThat(comodin.get("conversations")).as("un %% escrito no es comodín").isEmpty();
    }

    @Test
    @DisplayName("D-249: la búsqueda también pagina por cursor, sin duplicados")
    void busquedaPaginada() throws Exception {
        String sesion = sesionDe(admin);
        Set<String> vistos = new HashSet<>();
        String cursor = null;
        int filas = 0;
        do {
            JsonNode pagina = pedir(sesion, "size=30&q=" + enc("aprendiz 1") + (cursor == null ? "" : "&cursor=" + cursor), 200);
            for (JsonNode fila : pagina.get("conversations")) {
                vistos.add(fila.at("/conversation/id").asText());
                filas++;
            }
            cursor = pagina.get("nextCursor").isNull() ? null : pagina.get("nextCursor").asText();
        } while (cursor != null);
        // «Aprendiz 1», 10..19 y 100..199 (sin el 150, que se llama María): 1 + 10 + 99.
        assertThat(filas).isEqualTo(110);
        assertThat(vistos).hasSize(110);
    }

    @Test
    @DisplayName("D-249: 403 para aprendiz, mentor, líder de mentores y un ADMIN suspendido; 200 para el Alquimista")
    void soloAdministracionYAlquimista() throws Exception {
        for (String[] rolYEstado : List.of(new String[] {"APRENDIZ", "ACTIVO"}, new String[] {"MENTOR", "ACTIVO"},
                new String[] {"LIDER_MENTORES", "ACTIVO"}, new String[] {"ADMIN", "SUSPENDIDO"})) {
            UUID quien = usuario(rolYEstado[0], rolYEstado[1], "Sin permiso " + rolYEstado[0]);
            pedir(sesionDe(quien), "size=5", 403);
        }
        // El aprendiz dueño de un soporte tampoco ve la lista de todos.
        UUID duenoDeUnSoporte = jdbcTemplate.queryForObject("""
                SELECT usuario_id FROM renaser.participantes_conversacion WHERE conversacion_id = ? AND usuario_id <> ?
                """, UUID.class, soportes.getFirst(), admin);
        pedir(sesionDe(duenoDeUnSoporte), "size=5", 403);

        UUID alquimista = usuario("ALQUIMISTA", "ACTIVO", "Alquimista Soportes");
        JsonNode vacia = pedir(sesionDe(alquimista), "size=5", 200);
        assertThat(vacia.get("conversations")).as("solo los soportes donde participa").isEmpty();
    }

    @Test
    @DisplayName("D-249: un cursor que no dio el servidor es 400")
    void cursorRoto() throws Exception {
        pedir(sesionDe(admin), "cursor=no-es-un-cursor", 400);
    }

    @Test
    @DisplayName("D-249: la página busca el último mensaje por índice y los soportes por participante, sin recorrer mensajes")
    void elPlanUsaLosIndices() {
        String consulta = SoportesPorActividadJdbcAdapter.consultaDePagina(false, true)
                .replace(":quien", "'" + admin + "'")
                .replace(":actividad", "now()")
                .replace(":id", "'" + UUID.randomUUID() + "'")
                .replace(":limite", "26");
        String plan = jdbcTemplate.queryForList("EXPLAIN " + consulta, String.class).stream()
                .collect(Collectors.joining("\n"));

        assertThat(plan).contains("mensajes_conversacion_idx");
        assertThat(plan).doesNotContain("Seq Scan on mensajes");
    }

    /** La lista completa ordenada como la ordena {@code ConversacionService.listar}, calculada aparte. */
    private List<String> ordenEsperado() {
        return jdbcTemplate.queryForList("""
                SELECT c.id::text
                FROM renaser.conversaciones c
                LEFT JOIN renaser.mensajes m ON m.conversacion_id = c.id
                WHERE c.id = ANY (?)
                GROUP BY c.id, c.creado_en
                ORDER BY COALESCE(max(m.creado_en), c.creado_en) DESC, c.id DESC
                """, String.class, (Object) soportes.toArray(UUID[]::new));
    }

    /**
     * El soporte del aprendiz {@code i}, con él y el Admin adentro. Los mensajes van de a pares con el MISMO
     * instante (empates que desempata el id), y uno de cada diez no tiene ninguno (cuenta su creación).
     */
    private UUID soporteDe(UUID aprendiz, int i) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre, creado_en)
                VALUES (?, CAST('SOPORTE' AS renaser.tipo_conversacion), ?, 'Soporte', now() - interval '30 days' + ? * interval '1 minute')
                """, id, "soporte:" + aprendiz, i);
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?), (?, ?)
                """, id, aprendiz, id, admin);
        if (i % 10 != 0) {
            jdbcTemplate.update("""
                    INSERT INTO renaser.mensajes (conversacion_id, emisor_id, texto, creado_en)
                    VALUES (?, ?, 'hola', date_trunc('second', now()) - ? * interval '1 hour'),
                           (?, ?, 'antes', date_trunc('second', now()) - ? * interval '1 hour' - interval '1 day')
                    """, id, aprendiz, i / 2, id, aprendiz, i / 2);
        }
        return id;
    }

    private JsonNode pedir(String sesion, String consulta, int estadoEsperado) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + puerto + "/api/v1/chat/support-conversations?" + consulta))
                .header("X-Auth-Token", sesion).GET().build();
        HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(estadoEsperado);
        return estadoEsperado == 200 ? json.readTree(respuesta.body()) : null;
    }

    private static String enc(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
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

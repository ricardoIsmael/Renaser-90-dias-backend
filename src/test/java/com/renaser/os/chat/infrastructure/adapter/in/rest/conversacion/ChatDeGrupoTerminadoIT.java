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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-470 de punta a punta: el chat del grupo de un período que ya terminó NO aparece en la lista de
 * chats de quien sigue con la asignación abierta en él, porque al abrirlo responde 403 «Tu asignacion
 * cambio: ya no perteneces a ese grupo». La lista y el 403 usan ahora la misma regla.
 *
 * <p>Semilla, calcada del caso de la prueba de carga (D-238): Ana es aprendiz de «Ocaso» (período
 * terminado, asignación SIN cerrar, fila en la proyección y mensajes sin leer). Beto es aprendiz de
 * «Fénix» (en curso, mentor Ricardo). Ana y Beto tienen un chat 1 a 1. (Un aprendiz no puede tener dos
 * asignaciones de aprendiz abiertas a la vez: lo impide {@code asignaciones_un_grupo_por_aprendiz}.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChatDeGrupoTerminadoIT {

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
    private final List<UUID> grupos = new ArrayList<>();

    private UUID ana;
    private UUID beto;
    private UUID ricardo;
    private UUID olga;
    private UUID cohorte;
    private UUID chatFenix;
    private UUID chatOcaso;
    private UUID chatDirecto;

    @BeforeEach
    void seed() {
        ana = usuario("APRENDIZ", "Ana Pérez");
        beto = usuario("APRENDIZ", "Beto Díaz");
        ricardo = usuario("MENTOR", "Ricardo Palomino");
        olga = usuario("MENTOR", "Olga Ruiz");
        cohorte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte E-470', CURRENT_DATE - 60)",
                cohorte);
        // Dos días atrás y no uno: CURRENT_DATE es UTC y el grupo cierra en su zona (Lima).
        UUID ocaso = grupo("Ocaso", olga, "CURRENT_DATE - 20", "CURRENT_DATE - 2");
        chatOcaso = conversacion(ocaso);
        asignar(ocaso, olga, "MENTOR");
        asignar(ocaso, ana, "APRENDIZ");
        List.of(olga, ana).forEach(u -> participa(chatOcaso, u));
        UUID fenix = grupo("Fenix", ricardo, "CURRENT_DATE - 1", "CURRENT_DATE + 30");
        chatFenix = conversacion(fenix);
        asignar(fenix, ricardo, "MENTOR");
        asignar(fenix, beto, "APRENDIZ");
        List.of(ricardo, beto).forEach(u -> participa(chatFenix, u));
        chatDirecto = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa)
                VALUES (?, CAST('DIRECTA' AS renaser.tipo_conversacion), ?)
                """, chatDirecto, "e470|" + chatDirecto);
        conversaciones.add(chatDirecto);
        List.of(ana, beto).forEach(u -> participa(chatDirecto, u));
        // Historia del grupo que terminó, escrita mientras estaba en curso: Ana no la leyó.
        for (int i = 0; i < 3; i++) {
            jdbcTemplate.update("""
                    INSERT INTO renaser.mensajes (conversacion_id, emisor_id, texto, creado_en)
                    VALUES (?, ?, ?, now() - interval '3 days')
                    """, chatOcaso, olga, "Ocaso " + i);
        }
    }

    @AfterEach
    void limpiar() {
        conversaciones.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        grupos.forEach(id -> {
            jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id);
        });
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("E-470: la lista no trae el grupo del período terminado; el 1 a 1 sí, y los no leídos son solo de lo que se lista")
    void laListaNoTraeElGrupoTerminado() throws Exception {
        enviar(chatDirecto, beto, "Hola Ana");

        JsonNode lista = obtener("/api/v1/chat/conversations", ana);

        assertThat(ids(lista)).contains(chatDirecto.toString()).doesNotContain(chatOcaso.toString());
        long totalNoLeidos = 0;
        for (JsonNode fila : lista) {
            totalNoLeidos += fila.get("unreadCount").asLong();
        }
        assertThat(totalNoLeidos).as("el contador de la app suma los de la lista: sin los 3 de Ocaso").isEqualTo(1);
    }

    @Test
    @DisplayName("E-470: el grupo en curso sigue en la lista de su aprendiz y de su mentor, junto al 1 a 1")
    void elGrupoActualSigueApareciendo() throws Exception {
        enviar(chatFenix, ricardo, "Bienvenido al grupo");

        assertThat(ids(obtener("/api/v1/chat/conversations", beto)))
                .contains(chatFenix.toString(), chatDirecto.toString());
        assertThat(ids(obtener("/api/v1/chat/conversations", ricardo))).contains(chatFenix.toString());
    }

    @Test
    @DisplayName("E-470: lo que se oculta sigue cerrado con el mismo 403, y sus mensajes quedan en la base")
    void ocultarNoBorraNiAbre() throws Exception {
        HttpResponse<String> abrir = pedir("GET", "/api/v1/chat/conversations/" + chatOcaso + "/messages", ana, null);

        assertThat(abrir.statusCode()).isEqualTo(403);
        assertThat(abrir.body()).contains("Tu asignacion cambio: ya no perteneces a ese grupo");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM renaser.mensajes WHERE conversacion_id = ?",
                Integer.class, chatOcaso)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.participantes_conversacion WHERE conversacion_id = ? AND usuario_id = ?",
                Integer.class, chatOcaso, ana)).as("la proyección no se toca").isEqualTo(1);
    }

    @Test
    @DisplayName("E-470: la mentora del grupo terminado tampoco lo ve en su lista")
    void laMentoraDelGrupoTerminadoTampoco() throws Exception {
        assertThat(ids(obtener("/api/v1/chat/conversations", olga))).doesNotContain(chatOcaso.toString());
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private static List<String> ids(JsonNode lista) {
        List<String> ids = new ArrayList<>();
        lista.forEach(fila -> ids.add(fila.get("conversation").get("id").asText()));
        return ids;
    }

    private void enviar(UUID conversacion, UUID quien, String texto) throws Exception {
        HttpResponse<String> respuesta = pedir("POST", "/api/v1/chat/conversations/" + conversacion + "/messages", quien,
                Map.of("type", "TEXT", "text", texto));
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
    }

    private JsonNode obtener(String ruta, UUID quien) throws Exception {
        HttpResponse<String> respuesta = pedir("GET", ruta, quien, null);
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, UUID quien, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", guardarSesion(sesiones, quien));
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
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
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol);
        usuarios.add(id);
        return id;
    }

    private UUID grupo(String nombre, UUID mentor, String inicio, String fin) {
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?) ON CONFLICT DO NOTHING", mentor);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), %s, %s, ?)
                """.formatted(inicio, fin), id, nombre, cohorte, mentor);
        grupos.add(id);
        return id;
    }

    private UUID conversacion(UUID celula) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, celula_id)
                VALUES (?, CAST('CELULA' AS renaser.tipo_conversacion), ?)
                """, id, celula);
        conversaciones.add(id);
        return id;
    }

    private void participa(UUID conversacion, UUID usuario) {
        jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                conversacion, usuario);
    }

    private void asignar(UUID grupo, UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '10 days', NULL, 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, "prueba|" + id);
    }
}

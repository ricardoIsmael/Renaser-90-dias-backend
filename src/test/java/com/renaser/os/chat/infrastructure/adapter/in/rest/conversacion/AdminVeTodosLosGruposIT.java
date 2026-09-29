package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.chat.api.AvisosDeMensajesFinder;
import com.renaser.os.chat.api.AvisosDeMensajesFinder.Destinatario;
import com.renaser.os.shared.domain.UserId;
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
 * D-225 de punta a punta sobre el Tomcat real: el ADMIN ve, lee y escribe en el chat de un grupo en
 * curso donde NO está asignado, sin volverse integrante; y ninguna otra regla se afloja.
 *
 * <p>Semilla: «Fénix» (en curso) con su mentor Ricardo y sus aprendices Ana y Beto, sin nadie del staff
 * asignado; «Ocaso», un grupo cuyo periodo ya terminó, con su mentora Olga. Fuera de los dos: Kelin
 * (ADMIN), Zoe (ALQUIMISTA), Marta (MENTOR de otro lado), Luis (APRENDIZ de otro lado) y un ADMIN
 * suspendido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AdminVeTodosLosGruposIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AvisosDeMensajesFinder avisos;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> conversaciones = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();

    private UUID ana;
    private UUID beto;
    private UUID ricardo;
    private UUID olga;
    private UUID kelin;
    private UUID zoe;
    private UUID marta;
    private UUID luis;
    private UUID adminSuspendido;
    private UUID cohorte;
    private UUID chatFenix;
    private UUID chatOcaso;

    @BeforeEach
    void seed() {
        ana = usuario("APRENDIZ", "Ana Pérez", "ACTIVO");
        beto = usuario("APRENDIZ", "Beto Díaz", "ACTIVO");
        ricardo = usuario("MENTOR", "Ricardo Palomino", "ACTIVO");
        olga = usuario("MENTOR", "Olga Ruiz", "ACTIVO");
        kelin = usuario("ADMIN", "Kelin Rojas", "ACTIVO");
        zoe = usuario("ALQUIMISTA", "Zoe Alquimia", "ACTIVO");
        marta = usuario("MENTOR", "Marta Otra", "ACTIVO");
        luis = usuario("APRENDIZ", "Luis Soto", "ACTIVO");
        adminSuspendido = usuario("ADMIN", "Admin Suspendido", "SUSPENDIDO");
        cohorte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte D-225', CURRENT_DATE - 60)",
                cohorte);
        UUID fenix = grupo("Fenix", ricardo, "CURRENT_DATE - 5", "CURRENT_DATE + 20");
        chatFenix = conversacion(fenix, "Fenix");
        asignar(fenix, ricardo, "MENTOR");
        asignar(fenix, ana, "APRENDIZ");
        asignar(fenix, beto, "APRENDIZ");
        List.of(ricardo, ana, beto).forEach(u -> participa(chatFenix, u));
        UUID ocaso = grupo("Ocaso", olga, "CURRENT_DATE - 50", "CURRENT_DATE - 2");
        chatOcaso = conversacion(ocaso, "Ocaso");
        asignar(ocaso, olga, "MENTOR");
        participa(chatOcaso, olga);
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
    @DisplayName("D-225: la lista del Admin trae el grupo en curso donde no está asignado, con el nombre de su mentor y sin no leídos; el grupo vencido no")
    void laListaDelAdminTraeLosGruposEnCurso() throws Exception {
        enviar(chatFenix, ana, "Hola grupo");

        JsonNode fila = filaDe(obtener("/api/v1/chat/conversations", kelin), chatFenix);

        assertThat(fila).as("el grupo ajeno en curso figura").isNotNull();
        assertThat(fila.get("conversation").get("type").asText()).isEqualTo("CELL");
        assertThat(fila.get("conversation").get("nombre").asText()).isEqualTo("Ricardo y sus aprendices");
        assertThat(fila.get("unreadCount").asLong()).as("no es integrante: no le reclama atención").isZero();
        assertThat(fila.get("lastMessage").get("text").asText()).isEqualTo("Hola grupo");
        assertThat(filaDe(obtener("/api/v1/chat/conversations", kelin), chatOcaso)).as("vencido: oculto").isNull();
    }

    @Test
    @DisplayName("D-225: los demás roles no ven en su lista grupos ajenos (alquimista, mentor ajeno, aprendiz ajeno)")
    void losDemasNoVenGruposAjenosEnSuLista() throws Exception {
        for (UUID quien : List.of(zoe, marta, luis)) {
            assertThat(filaDe(obtener("/api/v1/chat/conversations", quien), chatFenix)).as("lista de " + quien).isNull();
        }
    }

    @Test
    @DisplayName("D-225: el Admin lee, escribe, marca leído y ve a los integrantes del grupo ajeno, sin figurar entre ellos ni quedar como participante")
    void elAdminLeeYEscribeSinVolverseIntegrante() throws Exception {
        enviar(chatFenix, ana, "Buen día");

        assertThat(textos(obtener(mensajes(chatFenix), kelin))).contains("Buen día");
        assertThat(enviar(chatFenix, kelin, "Hola, soy Kelin").statusCode()).isEqualTo(201);
        assertThat(textos(obtener(mensajes(chatFenix), ana))).contains("Hola, soy Kelin");
        assertThat(pedir("POST", "/api/v1/chat/conversations/" + chatFenix + "/read", kelin, null).statusCode())
                .isEqualTo(200);

        JsonNode integrantes = obtener("/api/v1/chat/conversations/" + chatFenix + "/participants", kelin);
        List<String> nombres = new ArrayList<>();
        integrantes.get("participants").forEach(f -> nombres.add(f.get("nombre").asText()));
        assertThat(nombres).containsExactly("Ricardo Palomino", "Ana Pérez", "Beto Díaz");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.participantes_conversacion WHERE conversacion_id = ? AND usuario_id = ?",
                Integer.class, chatFenix, kelin)).as("no se lo agrega como participante").isZero();
    }

    @Test
    @DisplayName("D-225: el Admin no recibe el push de los mensajes de un grupo ajeno; los integrantes reciben el suyo")
    void elAdminNoRecibePushDeGruposAjenos() throws Exception {
        UUID deAna = UUID.fromString(json.readTree(enviar(chatFenix, ana, "Aviso").body()).get("id").asText());
        UUID deKelin = UUID.fromString(json.readTree(enviar(chatFenix, kelin, "Respuesta").body()).get("id").asText());

        assertThat(destinatarios(deAna)).containsExactlyInAnyOrder(UserId.of(ricardo), UserId.of(beto));
        assertThat(destinatarios(deKelin)).containsExactlyInAnyOrder(UserId.of(ricardo), UserId.of(ana), UserId.of(beto));
    }

    @Test
    @DisplayName("D-225, autorización negativa: mentor ajeno, aprendiz ajeno, alquimista y admin suspendido reciben 403; el grupo vencido, 403 también para el Admin")
    void ningunaOtraReglaSeAfloja() throws Exception {
        for (UUID quien : List.of(marta, luis, zoe, adminSuspendido)) {
            assertThat(pedir("GET", mensajes(chatFenix), quien, null).statusCode()).as("leer, " + quien).isEqualTo(403);
            assertThat(enviar(chatFenix, quien, "intruso").statusCode()).as("escribir, " + quien).isEqualTo(403);
            assertThat(pedir("GET", "/api/v1/chat/conversations/" + chatFenix + "/participants", quien, null).statusCode())
                    .as("integrantes, " + quien).isEqualTo(403);
        }
        assertThat(pedir("GET", mensajes(chatOcaso), kelin, null).statusCode()).as("grupo vencido").isEqualTo(403);
        assertThat(enviar(chatOcaso, kelin, "hola").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("D-225: un Admin con la sesión viva que pasa a suspendido deja de ver el grupo ajeno")
    void unAdminSuspendidoConSesionValida() throws Exception {
        String sesion = sesionDe(kelin);
        assertThat(pedir("GET", mensajes(chatFenix), sesion).statusCode()).isEqualTo(200);

        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", kelin);

        assertThat(pedir("GET", mensajes(chatFenix), sesion).statusCode()).isEqualTo(403);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private List<UserId> destinatarios(UUID mensajeId) {
        return avisos.avisoDe(mensajeId).orElseThrow().destinatarios().stream().map(Destinatario::usuarioId).toList();
    }

    private static String mensajes(UUID conversacion) {
        return "/api/v1/chat/conversations/" + conversacion + "/messages";
    }

    private static JsonNode filaDe(JsonNode lista, UUID conversacion) {
        for (JsonNode fila : lista) {
            if (fila.get("conversation").get("id").asText().equals(conversacion.toString())) {
                return fila;
            }
        }
        return null;
    }

    private static List<String> textos(JsonNode pagina) {
        List<String> textos = new ArrayList<>();
        pagina.get("messages").forEach(m -> textos.add(m.get("text").asText()));
        return textos;
    }

    private HttpResponse<String> enviar(UUID conversacion, UUID quien, String texto) throws Exception {
        return pedir("POST", mensajes(conversacion), quien, Map.of("type", "TEXT", "text", texto));
    }

    private JsonNode obtener(String ruta, UUID quien) throws Exception {
        HttpResponse<String> respuesta = pedir("GET", ruta, quien, null);
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, UUID quien, Object cuerpo) throws Exception {
        return pedir(metodo, ruta, sesionDe(quien), cuerpo);
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion) throws Exception {
        return pedir(metodo, ruta, sesion, null);
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", sesion);
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
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

    private UUID usuario(String rol, String nombre, String estado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
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

    private UUID conversacion(UUID celula, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.conversaciones (id, tipo, celula_id, nombre)
                VALUES (?, CAST('CELULA' AS renaser.tipo_conversacion), ?, ?)
                """, id, celula, nombre);
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
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 hour', NULL, 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, "prueba|" + id);
    }
}

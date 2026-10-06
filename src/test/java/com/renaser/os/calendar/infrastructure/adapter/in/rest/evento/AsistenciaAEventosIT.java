package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quién respondió y pasar lista (D-256) de punta a punta: Tomcat real, sesión real y Postgres real (V93).
 *
 * <p>Semilla: el grupo «Fénix» de la mentora Mara, que creó el evento «Mentoría» para su grupo el lunes 5 a las
 * 20:00 de Lima (01:00 UTC del martes 6), con sus aprendices Ana, Beto y Ciro. Fuera del evento: Kelin (ADMIN),
 * Zoe (ALQUIMISTA), Lía (LÍDER DE MENTORES), Olga (MENTOR que no lo creó) y un ADMIN suspendido.
 *
 * <p>El reloj de la aplicación es movible y arranca a las 00:45 UTC: en Lima son las 19:45 del lunes, el día
 * ANTERIOR al día UTC (regla 02 §3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AsistenciaAEventosIT {

    private static final Instant INICIO = Instant.parse("2026-10-06T01:00:00Z");
    private static final Instant LAS_1945_EN_LIMA = Instant.parse("2026-10-06T00:45:00Z");
    private static final RelojMovible RELOJ = new RelojMovible();

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private UUID cohorte;
    private UUID grupo;
    private UUID evento;
    private UUID mara;
    private UUID ana;
    private UUID beto;
    private UUID ciro;
    private UUID kelin;
    private UUID zoe;
    private UUID lia;
    private UUID olga;
    private UUID suspendido;

    @BeforeEach
    void sembrar() {
        RELOJ.fijar(LAS_1945_EN_LIMA);
        mara = usuario("MENTOR", "Mara Mentora", "ACTIVO");
        kelin = usuario("ADMIN", "Kelin Rojas", "ACTIVO");
        zoe = usuario("ALQUIMISTA", "Zoe Alquimia", "ACTIVO");
        lia = usuario("LIDER_MENTORES", "Lía Líder", "ACTIVO");
        olga = usuario("MENTOR", "Olga Otra", "ACTIVO");
        suspendido = usuario("ADMIN", "Admin Suspendido", "SUSPENDIDO");
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte D-256', DATE '2026-09-20')",
                cohorte);
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", mara);
        grupo = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, mentor_id, cohorte_id) VALUES (?, 'Fénix', ?, ?)",
                grupo, mara, cohorte);
        ana = aprendiz("Ana Ríos");
        beto = aprendiz("Beto Díaz");
        ciro = aprendiz("Ciro Paz");
        evento = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.eventos (id, titulo, inicia_en, duracion_minutos, timezone, tipo_ubicacion,
                                             valor_ubicacion, tipo_audiencia, celula_destino_id, estado, tipo_evento,
                                             creado_por)
                VALUES (?, 'Mentoría', ?, 60, 'America/Lima', 'MEET', 'https://meet.google.com/abc-defg-hij',
                        'CELULA', ?, 'PUBLICADO', 'ESPONTANEO', ?)
                """, evento, java.sql.Timestamp.from(INICIO), grupo, mara);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.eventos WHERE id = ?", evento);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.participantes_programa WHERE usuario_id = ?", id));
        jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        jdbc.update("DELETE FROM renaser.perfiles_mentor WHERE usuario_id = ?", mara);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("Quién respondió: Admin ve Van / No van / Sin respuesta (la audiencia del grupo) y el historial de quien cambió")
    void quienRespondioConHistorial() throws Exception {
        assertThat(rsvp(ana, "NOT_GOING").statusCode()).isEqualTo(200);
        RELOJ.fijar(LAS_1945_EN_LIMA.plusSeconds(60));
        assertThat(rsvp(ana, "GOING").statusCode()).isEqualTo(200);
        assertThat(rsvp(ana, "GOING").statusCode()).as("repetir no ensucia el historial").isEqualTo(200);
        assertThat(rsvp(beto, "NOT_GOING").statusCode()).isEqualTo(200);

        HttpResponse<String> respuesta = pedir("GET", "/responses" + ocurrencia(), kelin, null);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("occurrenceStart").asText()).isEqualTo(INICIO.toString());
        Map<String, JsonNode> porId = porId(cuerpo.get("people"));
        assertThat(porId.keySet()).as("la audiencia (aprendices + mentora del grupo), sin el staff que no respondió")
                .containsExactlyInAnyOrder(ana.toString(), beto.toString(), ciro.toString(), mara.toString());
        JsonNode filaAna = porId.get(ana.toString());
        assertThat(filaAna.get("fullName").asText()).isEqualTo("Ana Ríos");
        assertThat(filaAna.get("status").asText()).isEqualTo("GOING");
        assertThat(filaAna.get("respondedAt").asText()).isEqualTo(LAS_1945_EN_LIMA.plusSeconds(60).toString());
        assertThat(filaAna.get("history")).extracting(h -> h.get("status").asText()).containsExactly("NOT_GOING", "GOING");
        assertThat(filaAna.get("history").get(0).get("at").asText()).isEqualTo(LAS_1945_EN_LIMA.toString());
        assertThat(porId.get(beto.toString()).get("status").asText()).isEqualTo("NOT_GOING");
        assertThat(porId.get(ciro.toString()).get("status").isNull()).as("sin respuesta").isTrue();
        assertThat(porId.get(ciro.toString()).get("history")).isEmpty();
        assertThat(filaAna.has("estado")).as("la hoja de respuestas no trae la asistencia").isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.historial_confirmaciones_evento WHERE evento_id = ?",
                Integer.class, evento)).isEqualTo(3);
    }

    @Test
    @DisplayName("Quién: la mentora creadora, Admin, Alquimista y Líder ven y pasan lista; mentor ajeno, aprendiz y suspendido, 403 en los cinco endpoints")
    void quienPuede() throws Exception {
        for (UUID puede : List.of(mara, kelin, zoe, lia)) {
            assertThat(pedir("GET", "/responses" + ocurrencia(), puede, null).statusCode()).as("responses " + puede).isEqualTo(200);
            assertThat(pedir("GET", "/attendance" + ocurrencia(), puede, null).statusCode()).as("attendance " + puede).isEqualTo(200);
            assertThat(marcar(puede, ana, "A_TIEMPO").statusCode()).as("marcar " + puede).isEqualTo(200);
        }
        for (UUID noPuede : List.of(olga, ana, suspendido)) {
            assertThat(pedir("GET", "/responses" + ocurrencia(), noPuede, null).statusCode()).as("responses").isEqualTo(403);
            assertThat(pedir("GET", "/attendance" + ocurrencia(), noPuede, null).statusCode()).as("attendance").isEqualTo(403);
            assertThat(marcar(noPuede, beto, "A_TIEMPO").statusCode()).as("marcar").isEqualTo(403);
            assertThat(pedir("POST", "/attendance/close", noPuede, cuerpoOcurrencia()).statusCode()).as("close").isEqualTo(403);
            assertThat(pedir("POST", "/attendance/reopen", noPuede, cuerpoOcurrencia()).statusCode()).as("reopen").isEqualTo(403);
        }
        assertThat(marcaDe(beto)).as("ningún 403 dejó una marca").isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.listas_asistencia_evento WHERE evento_id = ?",
                Integer.class, evento)).isZero();
    }

    @Test
    @DisplayName("Marcar es idempotente: repetir A_TIEMPO no mueve la hora; TARDE la cambia; null deja ausente (sin fila)")
    void marcarEsIdempotente() throws Exception {
        HttpResponse<String> primera = marcar(kelin, ana, "A_TIEMPO");
        RELOJ.fijar(LAS_1945_EN_LIMA.plusSeconds(120));
        HttpResponse<String> segunda = marcar(kelin, ana, "A_TIEMPO");

        assertThat(primera.statusCode()).isEqualTo(200);
        assertThat(json.readTree(segunda.body()).get("markedAt").asText()).isEqualTo(LAS_1945_EN_LIMA.toString());
        assertThat(json.readTree(segunda.body()).get("estado").asText()).isEqualTo("A_TIEMPO");
        assertThat(marcaDe(ana)).isEqualTo("A_TIEMPO");

        JsonNode tarde = json.readTree(marcar(lia, ana, "TARDE").body());
        assertThat(tarde.get("estado").asText()).isEqualTo("TARDE");
        assertThat(tarde.get("markedAt").asText()).isEqualTo(LAS_1945_EN_LIMA.plusSeconds(120).toString());
        assertThat(jdbc.queryForObject("SELECT marcado_por FROM renaser.asistencias_evento WHERE evento_id = ? AND usuario_id = ?",
                UUID.class, evento, ana)).isEqualTo(lia);

        assertThat(marcar(kelin, ana, null).statusCode()).isEqualTo(200);
        assertThat(marcar(kelin, ana, null).statusCode()).as("quitar dos veces").isEqualTo(200);
        assertThat(marcaDe(ana)).isNull();
        assertThat(marcar(kelin, ana, "PRESENTE").statusCode()).as("estado desconocido").isEqualTo(400);
    }

    @Test
    @DisplayName("Ventana con el reloj en la madrugada UTC: 00:20 UTC (19:20 en Lima) es 409; 00:45 UTC (19:45) marca; 14:01 UTC ya venció")
    void ventanaDeTiempo() throws Exception {
        RELOJ.fijar(Instant.parse("2026-10-06T00:20:00Z"));
        HttpResponse<String> temprano = marcar(kelin, ana, "A_TIEMPO");
        JsonNode lista = json.readTree(pedir("GET", "/attendance" + ocurrencia(), kelin, null).body());

        assertThat(temprano.statusCode()).isEqualTo(409);
        assertThat(lista.get("open").asBoolean()).isFalse();
        assertThat(lista.get("opensAt").asText()).isEqualTo("2026-10-06T00:30:00Z");
        assertThat(lista.get("closesAt").asText()).isEqualTo("2026-10-06T14:00:00Z");

        RELOJ.fijar(LAS_1945_EN_LIMA);
        assertThat(marcar(kelin, ana, "A_TIEMPO").statusCode()).isEqualTo(200);

        RELOJ.fijar(Instant.parse("2026-10-06T14:01:00Z"));
        assertThat(marcar(kelin, beto, "TARDE").statusCode()).isEqualTo(409);
        assertThat(pedir("GET", "/attendance" + ocurrencia(), kelin, null).statusCode()).as("leer sigue").isEqualTo(200);
    }

    @Test
    @DisplayName("Cerrar: la lista cerrada no se marca (409); cerrar dos veces conserva quién y cuándo; reabrir deja corregir")
    void cerrarYReabrir() throws Exception {
        marcar(kelin, ana, "A_TIEMPO");
        RELOJ.fijar(Instant.parse("2026-10-06T02:06:00Z"));

        JsonNode cerrada = json.readTree(pedir("POST", "/attendance/close", kelin, cuerpoOcurrencia()).body());
        RELOJ.fijar(Instant.parse("2026-10-06T02:10:00Z"));
        JsonNode otraVez = json.readTree(pedir("POST", "/attendance/close", lia, cuerpoOcurrencia()).body());

        assertThat(cerrada.get("closed").get("byName").asText()).isEqualTo("Kelin Rojas");
        assertThat(cerrada.get("open").asBoolean()).isFalse();
        assertThat(otraVez.get("closed").get("at").asText()).isEqualTo("2026-10-06T02:06:00Z");
        assertThat(otraVez.get("closed").get("byUserId").asText()).isEqualTo(kelin.toString());
        assertThat(marcar(kelin, beto, "TARDE").statusCode()).isEqualTo(409);

        JsonNode reabierta = json.readTree(pedir("POST", "/attendance/reopen", lia, cuerpoOcurrencia()).body());
        assertThat(reabierta.get("closed").isNull()).isTrue();
        assertThat(reabierta.get("open").asBoolean()).isTrue();
        assertThat(marcar(lia, beto, "TARDE").statusCode()).isEqualTo(200);
        JsonNode filaAna = porId(reabierta.get("people")).get(ana.toString());
        assertThat(filaAna.get("estado").asText()).as("reabrir no borra las marcas").isEqualTo("A_TIEMPO");
    }

    @Test
    @DisplayName("Errores: persona fuera de la lista 400, fecha que no es del evento 400, evento inexistente 404")
    void errores() throws Exception {
        assertThat(marcar(kelin, olga, "A_TIEMPO").statusCode()).isEqualTo(400);
        assertThat(pedir("GET", "/attendance?occurrenceStart=" + enc(INICIO.plusSeconds(86_400).toString()), kelin, null)
                .statusCode()).isEqualTo(400);
        assertThat(pedir("GET", "/attendance" + ocurrencia(), kelin, null, UUID.randomUUID()).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Solo seguimiento: pasar lista no toca puntos")
    void noDaPuntos() throws Exception {
        long antes = puntos();

        marcar(kelin, ana, "A_TIEMPO");
        marcar(kelin, beto, "TARDE");
        pedir("POST", "/attendance/close", kelin, cuerpoOcurrencia());

        assertThat(puntos()).isEqualTo(antes);
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private HttpResponse<String> rsvp(UUID quien, String estado) throws Exception {
        return pedir("PUT", "/rsvp", quien, Map.of("occurrenceStart", INICIO.toString(), "status", estado));
    }

    private HttpResponse<String> marcar(UUID quien, UUID persona, String estado) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("occurrenceStart", INICIO.toString());
        cuerpo.put("estado", estado);
        return pedir("PUT", "/attendance/" + persona, quien, cuerpo);
    }

    private static Map<String, Object> cuerpoOcurrencia() {
        return Map.of("occurrenceStart", INICIO.toString());
    }

    private static String ocurrencia() {
        return "?occurrenceStart=" + enc(INICIO.toString());
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> pedir(String metodo, String ruta, UUID quien, Object cuerpo) throws Exception {
        return pedir(metodo, ruta, quien, cuerpo, evento);
    }

    private HttpResponse<String> pedir(String metodo, String ruta, UUID quien, Object cuerpo, UUID deEvento)
            throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/calendar/events/" + deEvento + ruta))
                .header("X-Auth-Token", sesionDe(quien));
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

    private static Map<String, JsonNode> porId(JsonNode personas) {
        Map<String, JsonNode> mapa = new HashMap<>();
        personas.forEach(p -> mapa.put(p.get("userId").asText(), p));
        return mapa;
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private String marcaDe(UUID persona) {
        List<String> estados = jdbc.queryForList(
                "SELECT estado FROM renaser.asistencias_evento WHERE evento_id = ? AND usuario_id = ?",
                String.class, evento, persona);
        return estados.isEmpty() ? null : estados.getFirst();
    }

    private long puntos() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.puntajes_participante", Long.class)
                + jdbc.queryForObject("SELECT count(*) FROM renaser.ajustes_puntos_liga", Long.class);
    }

    private UUID usuario(String rol, String nombre, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        usuarios.add(id);
        return id;
    }

    private UUID aprendiz(String nombre) {
        UUID id = usuario("APRENDIZ", nombre, "ACTIVO");
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, celula_id)
                VALUES (?, 10, 'America/Lima', ?)
                """, id, grupo);
        return id;
    }

    @TestConfiguration
    static class RelojDeLaPrueba {

        @Bean
        @Primary
        Clock relojMovible() {
            return RELOJ;
        }
    }

    /** Reloj que la prueba mueve entre pedidos; {@code today()} es el del instante en UTC, como el de producción. */
    static final class RelojMovible implements Clock {

        private volatile Instant ahora = LAS_1945_EN_LIMA;

        void fijar(Instant instante) {
            this.ahora = instante;
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }
}

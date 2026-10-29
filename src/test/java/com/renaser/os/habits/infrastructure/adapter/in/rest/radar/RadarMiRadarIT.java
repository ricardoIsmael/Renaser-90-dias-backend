package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mi Radar de punta a punta (spec 012, D-286): HTTP con sesion real, el {@code Clock} movible, las columnas de V119
 * (las horas de todos los dias, los dias con horas propias y la hora prevista) y las reglas de verdad contra PostgreSQL
 * y Redis descartables. Cada caso lleva el reloj a una hora de Lima que
 * en UTC cae en OTRO dia u otra hora (regla 02): 14:06 Lima son las 19:06Z, 22:00 Lima son las 03:00Z del dia
 * siguiente, 01:30 Lima son las 06:30Z.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, RadarMiRadarIT.RelojConfig.class})
class RadarMiRadarIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant A_LAS_14_06_EN_LIMA = Instant.parse("2026-10-20T19:06:00Z");
    private static final Instant A_LAS_15_20_EN_LIMA = Instant.parse("2026-10-20T20:20:00Z");
    private static final Instant A_LAS_22_00_EN_LIMA_ES_03_UTC = Instant.parse("2026-10-21T03:00:00Z");
    private static final Instant A_LAS_01_30_EN_LIMA_ES_06_UTC = Instant.parse("2026-10-21T06:30:00Z");
    private static final Instant A_LAS_11_00_EN_LIMA = Instant.parse("2026-10-20T16:00:00Z");
    private static final String CON_DIAS = """
            {"encendido":true,"horas":[14,9],"dias":[{"dia":7,"horas":[]},{"dia":6,"horas":[11]}]}""";
    private static final String REGISTRO = """
            {"whatAmIDoing":"Trabajando","whatAmIThinking":"En la reunion","whatAmIFeeling":"Tranquila",
             "energyLevel":7,"whatAmIAvoiding":"El correo"}""";

    @TestConfiguration
    static class RelojConfig {
        @Bean
        @Primary
        RelojMovible relojMiRadar() {
            return new RelojMovible();
        }
    }

    static final class RelojMovible implements Clock {
        volatile Instant ahora = A_LAS_14_06_EN_LIMA;

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired RelojMovible reloj;
    @Autowired SessionRepository<? extends Session> sesiones;
    @LocalServerPort int puerto;
    private final List<UUID> usuarios = new ArrayList<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        usuarios.clear();
        reloj.ahora = A_LAS_14_06_EN_LIMA;
    }

    // ── el flujo del dia 10 ─────────────────────────────────────────────────

    @Test
    @DisplayName("dia 10: apagado no se registra (409); se enciende con sus horas y a las 14:06 registra con la 14 prevista")
    void elFlujoDeLaPersona() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);

        assertThat(json.readTree(get("/mi-radar", sesion).body()).get("encendido").asBoolean()).isFalse();
        HttpResponse<String> apagado = post("", sesion, REGISTRO);
        assertThat(apagado.statusCode()).isEqualTo(409);
        assertThat(apagado.body()).contains("Mi Radar está apagado");
        assertThat(registros(persona)).isZero();

        HttpResponse<String> encendido = put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[20,9,14]}");
        assertThat(encendido.statusCode()).isEqualTo(200);
        assertThat(json.readTree(encendido.body()).get("horas").toString()).isEqualTo("[9,14,20]");
        assertThat(horasGuardadas(persona)).isEqualTo("9,14,20");

        HttpResponse<String> registro = post("", sesion, REGISTRO);
        assertThat(registro.statusCode()).isEqualTo(200);
        assertThat(json.readTree(registro.body()).get("horaPrevista").asInt()).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT hora_prevista FROM renaser.registros_radar WHERE participante_id = ?",
                Integer.class, persona)).isEqualTo(14);

        JsonNode historial = json.readTree(get("/history", sesion).body());
        assertThat(historial.get("entries")).hasSize(1);
        assertThat(historial.get("entries").get(0).get("horaPrevista").asInt()).isEqualTo(14);
        assertThat(historial.get("miRadar").get("encendido").asBoolean()).isTrue();
        assertThat(historial.get("miRadar").get("horas").toString()).isEqualTo("[9,14,20]");
        assertThat(historial.get("miRadar").get("dias")).isEmpty();
    }

    // ── por dia de la semana (cuarta ronda) ─────────────────────────────────

    @Test
    @DisplayName("por dia: se guardan las horas de todos los dias y los dias propios (el sabado la 11, el domingo sin Radar) y se leen igual")
    void guardaLosDias() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);

        HttpResponse<String> guardado = put("/mi-radar", sesion, CON_DIAS);

        assertThat(guardado.statusCode()).isEqualTo(200);
        JsonNode devuelto = json.readTree(guardado.body());
        assertThat(devuelto.get("horas").toString()).isEqualTo("[9,14]");
        assertThat(devuelto.get("dias").toString())
                .isEqualTo("[{\"dia\":6,\"horas\":[11]},{\"dia\":7,\"horas\":[]}]");
        assertThat(json.readTree(get("/mi-radar", sesion).body())).isEqualTo(devuelto);
        assertThat(horasGuardadas(persona)).isEqualTo("9,14");
        assertThat(diasGuardados(persona)).isEqualTo("[{\"dia\": 6, \"horas\": [11]}, {\"dia\": 7, \"horas\": []}]");
    }

    @Test
    @DisplayName("por dia: un PUT sin «dias» equivale a dias vacio y borra los dias propios de antes")
    void sinDiasBorraLosPropios() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, CON_DIAS);

        HttpResponse<String> sinDias = put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9,14]}");

        assertThat(sinDias.statusCode()).isEqualTo(200);
        assertThat(json.readTree(sinDias.body()).get("dias")).isEmpty();
        assertThat(diasGuardados(persona)).isNull();
    }

    @Test
    @DisplayName("por dia: el martes con horas propias registra a la 14 con esa hora prevista y no con las de todos los dias")
    void diaPropioRegistraConSuHora() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        // 20/10/2026 es martes (dia 2): propias la 14; las de todos los dias, solo la 9.
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9],\"dias\":[{\"dia\":2,\"horas\":[14]}]}");

        HttpResponse<String> registro = post("", sesion, REGISTRO);

        assertThat(registro.statusCode()).isEqualTo(200);
        assertThat(json.readTree(registro.body()).get("horaPrevista").asInt()).isEqualTo(14);
    }

    @Test
    @DisplayName("por dia: el martes «Sin Radar» es 409 «Hoy no tienes Mi Radar» y no guarda nada; el miercoles, que sigue las de todos, si registra")
    void diaSinRadar() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9,14],\"dias\":[{\"dia\":2,\"horas\":[]}]}");

        HttpResponse<String> martes = post("", sesion, REGISTRO);

        assertThat(martes.statusCode()).isEqualTo(409);
        assertThat(martes.body()).contains("Hoy no tienes Mi Radar");
        assertThat(registros(persona)).isZero();

        reloj.ahora = A_LAS_14_06_EN_LIMA.plusSeconds(24 * 3600);
        HttpResponse<String> miercoles = post("", sesion, REGISTRO);
        assertThat(miercoles.statusCode()).isEqualTo(200);
        assertThat(json.readTree(miercoles.body()).get("horaPrevista").asInt()).isEqualTo(14);
    }

    @Test
    @DisplayName("regla 02: a las 22:00 del martes de Lima (03:00Z del miercoles) manda el martes, no el dia UTC; con el miercoles «Sin Radar» igual registra")
    void elDiaDeLaSemanaEsElDeLimaConElRelojEnLaMadrugadaUtc() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9],\"dias\":[{\"dia\":2,\"horas\":[22]},"
                + "{\"dia\":3,\"horas\":[]}]}");
        reloj.ahora = A_LAS_22_00_EN_LIMA_ES_03_UTC;

        HttpResponse<String> registro = post("", sesion, REGISTRO);

        assertThat(registro.statusCode()).isEqualTo(200);
        assertThat(json.readTree(registro.body()).get("horaPrevista").asInt()).isEqualTo(22);
    }

    @Test
    @DisplayName("SC-008: dias fuera de las reglas son 400 y no cambian lo guardado")
    void diasInvalidos() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);

        for (String dias : List.of(
                "[{\"dia\":0,\"horas\":[11]}]",
                "[{\"dia\":8,\"horas\":[11]}]",
                "[{\"dia\":6,\"horas\":[11]},{\"dia\":6,\"horas\":[12]}]",
                "[{\"dia\":6,\"horas\":[2]}]",
                "[{\"dia\":6,\"horas\":[9,9]}]",
                "[{\"dia\":6,\"horas\":[3,4,5,6,7,8,9,10,11,12,13,14,15]}]",
                "[{\"dia\":6}]",
                "[{\"horas\":[11]}]",
                "[null]")) {
            assertThat(put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9],\"dias\":" + dias + "}")
                    .statusCode()).as(dias).isEqualTo(400);
        }
        assertThat(put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[],\"dias\":[{\"dia\":6,\"horas\":[11]}]}")
                .statusCode()).isEqualTo(400);
        assertThat(horasGuardadas(persona)).isNull();
        assertThat(diasGuardados(persona)).isNull();
    }

    @Test
    @DisplayName("por dia: apagar deja las dos columnas en NULL y el historial trae los dias propios mientras esta encendido")
    void apagarBorraLosDias() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, CON_DIAS);

        JsonNode historial = json.readTree(get("/history", sesion).body());
        assertThat(historial.get("miRadar").get("dias")).hasSize(2);

        HttpResponse<String> apagado = put("/mi-radar", sesion, "{\"encendido\":false,\"horas\":[9],\"dias\":[{\"dia\":6,\"horas\":[11]}]}");

        assertThat(apagado.statusCode()).isEqualTo(200);
        assertThat(json.readTree(apagado.body()).get("dias")).isEmpty();
        assertThat(horasGuardadas(persona)).isNull();
        assertThat(diasGuardados(persona)).isNull();
    }

    @Test
    @DisplayName("una hora que no elegi se registra pero sin hora prevista")
    void horaLibre() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9,14,20]}");
        reloj.ahora = A_LAS_15_20_EN_LIMA;

        HttpResponse<String> registro = post("", sesion, REGISTRO);

        assertThat(registro.statusCode()).isEqualTo(200);
        assertThat(json.readTree(registro.body()).get("horaPrevista").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT hora_prevista FROM renaser.registros_radar WHERE participante_id = ?",
                Integer.class, persona)).isNull();
    }

    @Test
    @DisplayName("regla 02: a las 22:00 de Lima (03:00 UTC del dia siguiente) la hora 22 elegida es la prevista")
    void conElRelojEnLaMadrugadaUtc() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[22]}");
        reloj.ahora = A_LAS_22_00_EN_LIMA_ES_03_UTC;

        HttpResponse<String> registro = post("", sesion, REGISTRO);

        assertThat(registro.statusCode()).isEqualTo(200);
        assertThat(json.readTree(registro.body()).get("horaPrevista").asInt()).isEqualTo(22);
    }

    @Test
    @DisplayName("a la 01:30 de Lima (06:30 UTC) no se registra aunque este encendido: 409 y nada guardado")
    void antesDeLasTres() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9,14,20]}");
        reloj.ahora = A_LAS_01_30_EN_LIMA_ES_06_UTC;

        HttpResponse<String> registro = post("", sesion, REGISTRO);

        assertThat(registro.statusCode()).isEqualTo(409);
        assertThat(registro.body()).contains("Mi Radar funciona de 03:00 a 23:59");
        assertThat(registros(persona)).isZero();
    }

    @Test
    @DisplayName("sigue la regla de un registro por hora: el segundo toque en la misma hora devuelve el primero")
    void unoPorHora() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[14]}");

        String primero = json.readTree(post("", sesion, REGISTRO).body()).get("id").asText();
        reloj.ahora = A_LAS_14_06_EN_LIMA.plusSeconds(600);
        String segundo = json.readTree(post("", sesion, REGISTRO).body()).get("id").asText();

        assertThat(segundo).isEqualTo(primero);
        assertThat(registros(persona)).isEqualTo(1);
    }

    // ── FR-001: la primera semana no cambia ─────────────────────────────────

    @Test
    @DisplayName("dia 5: se registra siempre, con Mi Radar apagado y a cualquier hora; prevista solo de 08 a 19")
    void laPrimeraSemanaNoCambia() throws Exception {
        UUID persona = participante(5, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);

        reloj.ahora = A_LAS_11_00_EN_LIMA;
        HttpResponse<String> enFranja = post("", sesion, REGISTRO);
        reloj.ahora = A_LAS_22_00_EN_LIMA_ES_03_UTC;
        HttpResponse<String> fueraDeFranja = post("", sesion, REGISTRO);

        assertThat(enFranja.statusCode()).isEqualTo(200);
        assertThat(json.readTree(enFranja.body()).get("horaPrevista").asInt()).isEqualTo(11);
        assertThat(fueraDeFranja.statusCode()).isEqualTo(200);
        assertThat(json.readTree(fueraDeFranja.body()).get("horaPrevista").isNull()).isTrue();
        // En la semana 1 Mi Radar no aplica: el historial lo trae en null, no «apagado». GET /mi-radar no cambia.
        JsonNode historial = json.readTree(get("/history", sesion).body());
        assertThat(historial.get("entries")).hasSize(2);
        assertThat(historial.get("miRadar").isNull()).isTrue();
        assertThat(json.readTree(get("/mi-radar", sesion).body()).get("encendido").asBoolean()).isFalse();
    }

    // ── SC-008: el servidor valida aunque se salte la app ───────────────────

    @Test
    @DisplayName("SC-008: horas fuera de las reglas son 400 y no cambian lo guardado")
    void horasInvalidas() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);

        for (String cuerpo : List.of(
                "{\"encendido\":true,\"horas\":[]}",
                "{\"encendido\":true}",
                "{\"encendido\":true,\"horas\":[2]}",
                "{\"encendido\":true,\"horas\":[9,24]}",
                "{\"encendido\":true,\"horas\":[0]}",
                "{\"encendido\":true,\"horas\":[9,9]}",
                "{\"encendido\":true,\"horas\":[8,9,10,11,12,13,14,15,16,17,18,19,20]}",
                "{\"encendido\":true,\"horas\":[9,null]}",
                "{\"horas\":[9]}")) {
            assertThat(put("/mi-radar", sesion, cuerpo).statusCode()).as(cuerpo).isEqualTo(400);
        }
        assertThat(horasGuardadas(persona)).isNull();
    }

    @Test
    @DisplayName("apagar guarda NULL (sin horas viejas), el historial sigue y registrar vuelve a ser 409")
    void apagar() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[14]}");
        post("", sesion, REGISTRO);
        reloj.ahora = A_LAS_15_20_EN_LIMA;

        HttpResponse<String> apagado = put("/mi-radar", sesion, "{\"encendido\":false,\"horas\":[9]}");

        assertThat(apagado.statusCode()).isEqualTo(200);
        assertThat(json.readTree(apagado.body()).get("encendido").asBoolean()).isFalse();
        assertThat(horasGuardadas(persona)).isNull();
        assertThat(json.readTree(get("/history", sesion).body()).get("entries")).hasSize(1);
        assertThat(post("", sesion, REGISTRO).statusCode()).isEqualTo(409);
    }

    // ── autorizacion negativa ───────────────────────────────────────────────

    @Test
    @DisplayName("sin sesion es 403 en GET, PUT y POST aunque venga X-Actor-Id; una cuenta SUSPENDIDA tambien")
    void sinSesionYSuspendida() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);

        assertThat(get("/mi-radar", null).statusCode()).isEqualTo(403);
        assertThat(put("/mi-radar", null, "{\"encendido\":true,\"horas\":[9]}").statusCode()).isEqualTo(403);
        assertThat(post("", null, REGISTRO).statusCode()).isEqualTo(403);

        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", persona);
        assertThat(get("/mi-radar", sesion).statusCode()).isEqualTo(403);
        assertThat(put("/mi-radar", sesion, "{\"encendido\":true,\"horas\":[9]}").statusCode()).isEqualTo(403);
        assertThat(post("", sesion, REGISTRO).statusCode()).isEqualTo(403);
        assertThat(horasGuardadas(persona)).isNull();
    }

    @Test
    @DisplayName("FR-019: el personal con su programa activado lo enciende; sin activarlo, es 403")
    void elPersonal() throws Exception {
        UUID conPrograma = participante(10, "MENTOR", true);
        UUID sinPrograma = participante(0, "MENTOR", false);

        assertThat(put("/mi-radar", sesionDe(sesiones, conPrograma), "{\"encendido\":true,\"horas\":[9]}").statusCode())
                .isEqualTo(200);
        assertThat(put("/mi-radar", sesionDe(sesiones, sinPrograma), "{\"encendido\":true,\"horas\":[9]}")
                .statusCode()).isEqualTo(403);
        assertThat(horasGuardadas(sinPrograma)).isNull();
    }

    // ── ayudas ──────────────────────────────────────────────────────────────

    /**
     * Un participante cuyo dia de programa es {@code dia} a las 14:06 de Lima del 20 de octubre. El dia sale de las
     * FECHAS (la proyeccion de {@code users} lo deriva de {@code fecha_inicio} y la zona), asi que la fila lleva un
     * {@code fecha_inicio} coherente con el reloj de la prueba en la zona de la persona y no solo la columna
     * {@code dia_programa}. Sin activar, manda la columna.
     */
    private UUID participante(int dia, String rol, boolean activado) {
        UUID id = UUID.randomUUID();
        LocalDate hoyEnLima = A_LAS_14_06_EN_LIMA.atZone(LIMA).toLocalDate();
        LocalDate diaUno = hoyEnLima.minusDays(Math.max(dia - 1, 0));
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba Mi Radar', CAST(? AS renaser.rol_usuario), 'ACTIVO')",
                id, id + "@renaser.test", rol);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, "
                + "programa_activado_en, timezone) VALUES (?, ?, ?, ?, 'America/Lima')", id, dia, diaUno,
                activado ? java.sql.Timestamp.from(diaUno.minusDays(1).atStartOfDay(LIMA).toInstant()) : null);
        usuarios.add(id);
        return id;
    }

    private long registros(UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.registros_radar WHERE participante_id = ?",
                Long.class, persona);
    }

    /** Las horas como las guarda la base ({@code NULL} si esta apagado). */
    private String horasGuardadas(UUID persona) {
        return jdbc.queryForObject("SELECT array_to_string(horas_mi_radar, ',') FROM renaser.participantes_programa "
                + "WHERE usuario_id = ?", String.class, persona);
    }

    /** Los dias propios como los guarda la base ({@code NULL} si no hay ninguno). */
    private String diasGuardados(UUID persona) {
        return jdbc.queryForObject("SELECT CAST(dias_mi_radar AS text) FROM renaser.participantes_programa "
                + "WHERE usuario_id = ?", String.class, persona);
    }

    private HttpRequest.Builder pedido(String ruta, String sesion) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/radar"
                + ruta)).header("Content-Type", "application/json");
        if (sesion != null) {
            builder.header("X-Auth-Token", sesion);
        } else {
            builder.header("X-Actor-Id", UUID.randomUUID().toString());
        }
        return builder;
    }

    private HttpResponse<String> get(String ruta, String sesion) throws Exception {
        return http.send(pedido(ruta, sesion).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String ruta, String sesion, String cuerpo) throws Exception {
        return http.send(pedido(ruta, sesion).POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String ruta, String sesion, String cuerpo) throws Exception {
        return http.send(pedido(ruta, sesion).PUT(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static <S extends Session> String sesionDe(SessionRepository<S> repositorio, UUID id) {
        S sesion = repositorio.createSession();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }
}

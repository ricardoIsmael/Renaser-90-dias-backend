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
 * Mi Radar de punta a punta (spec 012 rehecha por la 019, D-286 y D-292): HTTP con sesion real, el {@code Clock}
 * movible, las columnas de V122 y el Despertar y el Dormir de verdad ({@code preferencias_horario}) contra PostgreSQL y
 * Redis descartables. El reloj arranca el martes 20/10/2026 a las 14:06 de Lima: lunes y martes tienen candado, de
 * miercoles a domingo se edita. Cada caso lleva el reloj a una hora de Lima que en UTC cae en OTRO dia u otra hora
 * (regla 02): 14:06 Lima son las 19:06Z, 22:00 Lima son las 03:00Z del dia siguiente, 01:30 Lima son las 06:30Z.
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
    private static final UUID DESPERTAR = UUID.fromString("899a2151-e98c-4b61-a46c-b55134240d17");
    private static final UUID DORMIR = UUID.fromString("48d68c12-72a9-428c-a8c6-02b9b01bc2fe");
    private static final String FIN_DE_SEMANA_PERSONALIZADO = """
            {"encendido":true,"dias":[{"dia":6,"modo":"PERSONALIZADO","veces":3},{"dia":7,"modo":"PERSONALIZADO","veces":3}]}""";
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
    @DisplayName("dia 10 (Despertar 05:30, Dormir 22:30): apagado no se registra (409); se enciende solo y a las 14:06 registra con la 14 prevista")
    void elFlujoDeLaPersona() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);

        JsonNode antes = json.readTree(get("/mi-radar", sesion).body());
        assertThat(antes.get("encendido").asBoolean()).isFalse();
        assertThat(antes.get("hoy").asText()).isEqualTo("2026-10-20");
        assertThat(antes.get("dias")).hasSize(7);
        assertThat(antes.get("dias").get(0).get("fecha").asText()).isEqualTo("2026-10-19");
        assertThat(antes.get("dias").get(0).get("veces").asInt()).isEqualTo(17);
        assertThat(antes.get("dias").get(0).get("despertar").asText()).isEqualTo("05:30");
        assertThat(antes.get("dias").get(0).get("dormir").asText()).isEqualTo("22:30");
        assertThat(antes.get("dias").get(0).get("horas").toString())
                .isEqualTo("[6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22]");
        assertThat(antes.get("dias").get(0).get("editable").asBoolean()).isFalse();
        assertThat(antes.get("dias").get(1).get("editable").asBoolean()).as("hoy, martes").isFalse();
        assertThat(antes.get("dias").get(2).get("editable").asBoolean()).as("miercoles").isTrue();
        HttpResponse<String> apagado = post("", sesion, REGISTRO);
        assertThat(apagado.statusCode()).isEqualTo(409);
        assertThat(apagado.body()).contains("Mi Radar está apagado");
        assertThat(registros(persona)).isZero();

        HttpResponse<String> encendido = put("/mi-radar", sesion, "{\"encendido\":true}");
        assertThat(encendido.statusCode()).isEqualTo(200);
        assertThat(json.readTree(encendido.body()).get("encendido").asBoolean()).isTrue();
        assertThat(encendidoGuardado(persona)).isTrue();

        HttpResponse<String> registro = post("", sesion, REGISTRO);
        assertThat(registro.statusCode()).isEqualTo(200);
        assertThat(json.readTree(registro.body()).get("horaPrevista").asInt()).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT hora_prevista FROM renaser.registros_radar WHERE participante_id = ?",
                Integer.class, persona)).isEqualTo(14);

        JsonNode historial = json.readTree(get("/history", sesion).body());
        assertThat(historial.get("entries")).hasSize(1);
        assertThat(historial.get("entries").get(0).get("horaPrevista").asInt()).isEqualTo(14);
        assertThat(historial.get("miRadar").get("encendido").asBoolean()).isTrue();
        assertThat(historial.get("miRadar").get("dias")).hasSize(1);
        assertThat(historial.get("miRadar").get("dias").get(0).get("veces").asInt()).isEqualTo(17);
    }

    @Test
    @DisplayName("las horas cambian con el Despertar y el Dormir: 08:00 a 23:00 son 15 y 06:00 a 22:00, de 06 a 21")
    void lasHorasSalenDelHorario() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "08:00", "23:00");
        String sesion = sesionDe(sesiones, persona);

        assertThat(json.readTree(get("/mi-radar", sesion).body()).get("dias").get(3).get("veces").asInt()).isEqualTo(15);

        jdbc.update("UPDATE renaser.preferencias_horario SET hora_disparo = CAST(? AS time) WHERE participante_id = ? "
                + "AND habito_id = ?", "06:00", persona, DESPERTAR);
        jdbc.update("UPDATE renaser.preferencias_horario SET hora_disparo = CAST(? AS time) WHERE participante_id = ? "
                + "AND habito_id = ?", "22:00", persona, DORMIR);

        JsonNode dia = json.readTree(get("/mi-radar", sesion).body()).get("dias").get(3);
        assertThat(dia.get("veces").asInt()).isEqualTo(16);
        assertThat(dia.get("horas").get(0).asInt()).isEqualTo(6);
        assertThat(dia.get("horas").get(15).asInt()).isEqualTo(21);
    }

    @Test
    @DisplayName("sin Despertar (o sin Dormir) el dia no tiene Radar: sin horas, con su motivo, y registrar es 409 «Hoy no tienes Mi Radar»")
    void sinDespertar() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        jdbc.update("INSERT INTO renaser.preferencias_horario (participante_id, habito_id, hora_disparo) "
                + "VALUES (?, ?, CAST('22:30' AS time))", persona, DORMIR);
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true}");

        JsonNode dia = json.readTree(get("/mi-radar", sesion).body()).get("dias").get(3);
        assertThat(dia.get("horas")).isEmpty();
        assertThat(dia.get("veces").asInt()).isZero();
        assertThat(dia.get("despertar").isNull()).isTrue();
        assertThat(dia.get("motivo").asText()).isEqualTo("SIN_DESPERTAR");
        HttpResponse<String> registro = post("", sesion, REGISTRO);
        assertThat(registro.statusCode()).isEqualTo(409);
        assertThat(registro.body()).contains("Hoy no tienes Mi Radar");
        assertThat(registros(persona)).isZero();
    }

    // ── el modo y las veces, por dia de la semana ───────────────────────────

    @Test
    @DisplayName("Personalizado el fin de semana: 3 veces reparten 06, 14 y 22; se guarda solo eso y se lee igual")
    void guardaElModoYLasVeces() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);

        HttpResponse<String> guardado = put("/mi-radar", sesion, FIN_DE_SEMANA_PERSONALIZADO);

        assertThat(guardado.statusCode()).isEqualTo(200);
        JsonNode devuelto = json.readTree(guardado.body());
        assertThat(devuelto.get("dias").get(5).get("modo").asText()).isEqualTo("PERSONALIZADO");
        assertThat(devuelto.get("dias").get(5).get("veces").asInt()).isEqualTo(3);
        assertThat(devuelto.get("dias").get(5).get("maximo").asInt()).isEqualTo(17);
        assertThat(devuelto.get("dias").get(5).get("horas").toString()).isEqualTo("[6,14,22]");
        assertThat(devuelto.get("dias").get(4).get("modo").asText()).as("el viernes sigue Automatico").isEqualTo("AUTOMATICO");
        assertThat(json.readTree(get("/mi-radar", sesion).body())).isEqualTo(devuelto);
        assertThat(diasGuardados(persona)).isEqualTo("[{\"dia\": 6, \"modo\": \"PERSONALIZADO\", \"veces\": 3, \"sinRadar\": false}, "
                + "{\"dia\": 7, \"modo\": \"PERSONALIZADO\", \"veces\": 3, \"sinRadar\": false}]");
    }

    @Test
    @DisplayName("«Sin Radar» el domingo apaga solo ese dia; volver a Automatico lo deja de fabrica y no se guarda")
    void sinRadarYVolverADeFabrica() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);

        JsonNode sin = json.readTree(put("/mi-radar", sesion,
                "{\"encendido\":true,\"dias\":[{\"dia\":7,\"sinRadar\":true}]}").body());
        assertThat(sin.get("dias").get(6).get("horas")).isEmpty();
        assertThat(sin.get("dias").get(6).get("sinRadar").asBoolean()).isTrue();
        assertThat(sin.get("dias").get(5).get("veces").asInt()).isEqualTo(17);

        put("/mi-radar", sesion, "{\"encendido\":true,\"dias\":[{\"dia\":7,\"sinRadar\":false}]}");
        assertThat(diasGuardados(persona)).isNull();
    }

    @Test
    @DisplayName("FR-005: cambiar hoy (martes) o un dia pasado es 409 y no guarda nada; repetir hoy tal como esta se acepta")
    void hoyYLoPasadoNoSeEditan() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);

        for (int dia : new int[]{1, 2}) {
            HttpResponse<String> respuesta = put("/mi-radar", sesion,
                    "{\"encendido\":true,\"dias\":[{\"dia\":" + dia + ",\"modo\":\"PERSONALIZADO\",\"veces\":3}]}");
            assertThat(respuesta.statusCode()).as("dia %d", dia).isEqualTo(409);
            assertThat(respuesta.body()).contains("Hoy y los días que pasaron no se pueden cambiar");
        }
        assertThat(put("/mi-radar", sesion, "{\"encendido\":true,\"dias\":[{\"dia\":2,\"sinRadar\":true}]}").statusCode())
                .isEqualTo(409);
        assertThat(encendidoGuardado(persona)).isFalse();
        assertThat(diasGuardados(persona)).isNull();

        assertThat(put("/mi-radar", sesion, "{\"encendido\":true,\"dias\":[{\"dia\":2,\"modo\":\"AUTOMATICO\","
                + "\"sinRadar\":false}]}").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("SC-008: veces fuera de 1 a 21, mas de las que caben, un dia que no existe, repetido o un modo raro son 400 y no cambian lo guardado")
    void pedidosInvalidos() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "08:00", "12:00");
        String sesion = sesionDe(sesiones, persona);

        for (String dias : List.of(
                "[{\"dia\":6,\"modo\":\"PERSONALIZADO\",\"veces\":0}]",
                "[{\"dia\":6,\"modo\":\"PERSONALIZADO\",\"veces\":22}]",
                "[{\"dia\":6,\"modo\":\"PERSONALIZADO\",\"veces\":5}]",
                "[{\"dia\":0,\"modo\":\"PERSONALIZADO\",\"veces\":2}]",
                "[{\"dia\":8,\"modo\":\"PERSONALIZADO\",\"veces\":2}]",
                "[{\"dia\":6,\"sinRadar\":true},{\"dia\":6,\"sinRadar\":false}]",
                "[{\"dia\":6,\"modo\":\"RARO\"}]",
                "[{\"modo\":\"AUTOMATICO\"}]",
                "[null]")) {
            assertThat(put("/mi-radar", sesion, "{\"encendido\":true,\"dias\":" + dias + "}").statusCode())
                    .as(dias).isEqualTo(400);
        }
        assertThat(put("/mi-radar", sesion, "{\"dias\":[]}").statusCode()).isEqualTo(400);
        assertThat(encendidoGuardado(persona)).isFalse();
        assertThat(diasGuardados(persona)).isNull();
    }

    @Test
    @DisplayName("apagar conserva los dias; el historial sigue y registrar vuelve a ser 409")
    void apagar() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, FIN_DE_SEMANA_PERSONALIZADO);
        post("", sesion, REGISTRO);
        reloj.ahora = A_LAS_15_20_EN_LIMA;

        HttpResponse<String> apagado = put("/mi-radar", sesion, "{\"encendido\":false}");

        assertThat(apagado.statusCode()).isEqualTo(200);
        assertThat(json.readTree(apagado.body()).get("encendido").asBoolean()).isFalse();
        assertThat(json.readTree(apagado.body()).get("dias").get(5).get("veces").asInt()).isEqualTo(3);
        assertThat(encendidoGuardado(persona)).isFalse();
        assertThat(diasGuardados(persona)).isNotNull();
        assertThat(json.readTree(get("/history", sesion).body()).get("entries")).hasSize(1);
        assertThat(post("", sesion, REGISTRO).statusCode()).isEqualTo(409);
    }

    // ── registrar ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Personalizado: el martes con 6 veces pregunta 06, 09, 12, 16, 19 y 22; a las 16:10 registra con la 16 prevista y a las 14:06 queda libre")
    void elRepartoMandaLaHoraPrevista() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);
        // Hoy es martes y hoy no se edita por la API: el estado de partida se deja directo en la base.
        jdbc.update("UPDATE renaser.participantes_programa SET mi_radar_encendido = true, dias_mi_radar = "
                + "CAST('[{\"dia\":2,\"modo\":\"PERSONALIZADO\",\"veces\":6,\"sinRadar\":false}]' AS jsonb) "
                + "WHERE usuario_id = ?", persona);
        assertThat(json.readTree(get("/mi-radar", sesion).body()).get("dias").get(1).get("horas").toString())
                .isEqualTo("[6,9,12,16,19,22]");

        HttpResponse<String> libre = post("", sesion, REGISTRO);
        assertThat(libre.statusCode()).isEqualTo(200);
        assertThat(json.readTree(libre.body()).get("horaPrevista").isNull()).isTrue();

        reloj.ahora = Instant.parse("2026-10-20T21:10:00Z");
        HttpResponse<String> prevista = post("", sesion, REGISTRO);
        assertThat(prevista.statusCode()).isEqualTo(200);
        assertThat(json.readTree(prevista.body()).get("horaPrevista").asInt()).isEqualTo(16);
    }

    @Test
    @DisplayName("regla 02: a las 22:00 del martes de Lima (03:00Z del miercoles) manda el martes y la 22 es la ultima hora prevista")
    void conElRelojEnLaMadrugadaUtc() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true}");
        reloj.ahora = A_LAS_22_00_EN_LIMA_ES_03_UTC;

        HttpResponse<String> registro = post("", sesion, REGISTRO);

        assertThat(registro.statusCode()).isEqualTo(200);
        assertThat(json.readTree(registro.body()).get("horaPrevista").asInt()).isEqualTo(22);
    }

    @Test
    @DisplayName("a la 01:30 de Lima (06:30 UTC) no se registra aunque este encendido: 409 y nada guardado")
    void antesDeLasTres() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true}");
        reloj.ahora = A_LAS_01_30_EN_LIMA_ES_06_UTC;

        HttpResponse<String> registro = post("", sesion, REGISTRO);

        assertThat(registro.statusCode()).isEqualTo(409);
        assertThat(registro.body()).contains("Mi Radar funciona de 03:00 a 23:59");
        assertThat(registros(persona)).isZero();
    }

    @Test
    @DisplayName("sigue la regla de un registro por hora: el segundo toque en la misma hora devuelve el primero")
    void unoPorHora() throws Exception {
        UUID persona = participanteConHorario(10, "APRENDIZ", "05:30", "22:30");
        String sesion = sesionDe(sesiones, persona);
        put("/mi-radar", sesion, "{\"encendido\":true}");

        String primero = json.readTree(post("", sesion, REGISTRO).body()).get("id").asText();
        reloj.ahora = A_LAS_14_06_EN_LIMA.plusSeconds(600);
        String segundo = json.readTree(post("", sesion, REGISTRO).body()).get("id").asText();

        assertThat(segundo).isEqualTo(primero);
        assertThat(registros(persona)).isEqualTo(1);
    }

    // ── FR-001: la primera semana no cambia ─────────────────────────────────

    @Test
    @DisplayName("dia 5: se registra siempre, con Mi Radar apagado, sin Despertar y a cualquier hora; prevista solo de 08 a 19")
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
        // En la semana 1 Mi Radar no aplica: el historial lo trae en null, no «apagado».
        JsonNode historial = json.readTree(get("/history", sesion).body());
        assertThat(historial.get("entries")).hasSize(2);
        assertThat(historial.get("miRadar").isNull()).isTrue();
    }

    // ── autorizacion negativa ───────────────────────────────────────────────

    @Test
    @DisplayName("sin sesion es 403 en GET, PUT y POST aunque venga X-Actor-Id; una cuenta SUSPENDIDA tambien")
    void sinSesionYSuspendida() throws Exception {
        UUID persona = participante(10, "APRENDIZ", true);
        String sesion = sesionDe(sesiones, persona);

        assertThat(get("/mi-radar", null).statusCode()).isEqualTo(403);
        assertThat(put("/mi-radar", null, "{\"encendido\":true}").statusCode()).isEqualTo(403);
        assertThat(post("", null, REGISTRO).statusCode()).isEqualTo(403);

        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", persona);
        assertThat(get("/mi-radar", sesion).statusCode()).isEqualTo(403);
        assertThat(put("/mi-radar", sesion, "{\"encendido\":true}").statusCode()).isEqualTo(403);
        assertThat(post("", sesion, REGISTRO).statusCode()).isEqualTo(403);
        assertThat(encendidoGuardado(persona)).isFalse();
    }

    @Test
    @DisplayName("FR-019: Mi Radar es para todos los roles que hacen los 90 dias: con su programa activado cada rol lee, configura y registra (ningun filtro por rol)")
    void todosLosRolesConPrograma() throws Exception {
        for (String rol : List.of("APRENDIZ", "GUIA_APRENDIZ", "MENTOR", "LIDER_MENTORES", "ADMIN", "ALQUIMISTA")) {
            reloj.ahora = A_LAS_14_06_EN_LIMA;
            UUID persona = participanteConHorario(10, rol, "05:30", "22:30");
            String sesion = sesionDe(sesiones, persona);

            JsonNode semana = json.readTree(get("/mi-radar", sesion).body());
            assertThat(semana.get("dias")).as("%s: GET", rol).hasSize(7);
            assertThat(semana.get("dias").get(0).get("veces").asInt()).as("%s: 17 veces", rol).isEqualTo(17);
            assertThat(put("/mi-radar", sesion, FIN_DE_SEMANA_PERSONALIZADO).statusCode()).as("%s: PUT", rol).isEqualTo(200);
            assertThat(put("/mi-radar", sesion, "{\"encendido\":true,\"dias\":[{\"dia\":2,\"sinRadar\":true}]}").statusCode())
                    .as("%s: hoy no se edita para nadie", rol).isEqualTo(409);
            HttpResponse<String> registro = post("", sesion, REGISTRO);
            assertThat(registro.statusCode()).as("%s: POST", rol).isEqualTo(200);
            assertThat(json.readTree(registro.body()).get("horaPrevista").asInt()).as("%s: prevista", rol).isEqualTo(14);
            assertThat(json.readTree(get("/history", sesion).body()).get("miRadar").get("dias")).as("%s: historial", rol).hasSize(1);
        }
    }

    @Test
    @DisplayName("FR-019: el personal sin su programa activado no lo usa: 403 con el motivo PROGRAMA_NO_ACTIVADO en GET, PUT y POST (la puerta es tener el programa andando, no el rol)")
    void elPersonalSinPrograma() throws Exception {
        UUID sinPrograma = participante(0, "MENTOR", false);
        String sesion = sesionDe(sesiones, sinPrograma);

        for (HttpResponse<String> respuesta : List.of(get("/mi-radar", sesion), put("/mi-radar", sesion, "{\"encendido\":true}"),
                post("", sesion, REGISTRO))) {
            assertThat(respuesta.statusCode()).isEqualTo(403);
            JsonNode cuerpo = json.readTree(respuesta.body());
            assertThat(cuerpo.get("codigo").asText()).isEqualTo("PROGRAMA_NO_ACTIVADO");
            assertThat(cuerpo.get("message").asText()).isEqualTo("Mi Radar empieza en tu día 8. Primero elige tu Día 1.");
            assertThat(cuerpo.has("timestamp")).isTrue();
        }
        assertThat(encendidoGuardado(sinPrograma)).isFalse();
    }

    @Test
    @DisplayName("el personal que nunca empezo su programa (sin fila) recibe el mismo 403 PROGRAMA_NO_ACTIVADO, no un 404 mudo")
    void sinFilaDePrograma() throws Exception {
        UUID persona = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Sin programa', CAST('LIDER_MENTORES' AS renaser.rol_usuario), 'ACTIVO')", persona, persona + "@renaser.test");
        usuarios.add(persona);
        String sesion = sesionDe(sesiones, persona);

        for (HttpResponse<String> respuesta : List.of(get("/mi-radar", sesion), put("/mi-radar", sesion, "{\"encendido\":true}"))) {
            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(json.readTree(respuesta.body()).get("codigo").asText()).isEqualTo("PROGRAMA_NO_ACTIVADO");
        }
    }

    @Test
    @DisplayName("una cuenta suspendida recibe 403 con el motivo CUENTA_SUSPENDIDA (y no el del programa), aunque sea personal sin programa")
    void suspendidaDiceSuMotivo() throws Exception {
        UUID suspendida = participante(0, "MENTOR", false);
        String sesion = sesionDe(sesiones, suspendida);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", suspendida);

        for (HttpResponse<String> respuesta : List.of(get("/mi-radar", sesion), put("/mi-radar", sesion, "{\"encendido\":true}"))) {
            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(json.readTree(respuesta.body()).get("codigo").asText()).isEqualTo("CUENTA_SUSPENDIDA");
        }
    }

    @Test
    @DisplayName("el personal que ya eligio su Dia 1 lee Mi Radar aunque este en la semana 1 (ningun 403)")
    void conProgramaAunqueSeaLaSemana1() throws Exception {
        UUID persona = participante(3, "MENTOR", true);

        HttpResponse<String> respuesta = get("/mi-radar", sesionDe(sesiones, persona));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(respuesta.body()).get("dias")).hasSize(7);
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

    /** Un participante con su Despertar y su Dormir programados (las preferencias del aprendiz, spec 008). */
    private UUID participanteConHorario(int dia, String rol, String despertar, String dormir) {
        UUID id = participante(dia, rol, true);
        for (Object[] habito : new Object[][]{{DESPERTAR, despertar}, {DORMIR, dormir}}) {
            jdbc.update("INSERT INTO renaser.preferencias_horario (participante_id, habito_id, hora_disparo) "
                    + "VALUES (?, ?, CAST(? AS time))", id, habito[0], habito[1]);
        }
        return id;
    }

    /** Si Mi Radar esta encendido como lo guarda la base. */
    private boolean encendidoGuardado(UUID persona) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT mi_radar_encendido FROM renaser.participantes_programa "
                + "WHERE usuario_id = ?", Boolean.class, persona));
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

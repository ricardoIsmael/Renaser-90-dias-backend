package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** HTTP, permisos, Clock y zona real de participantes_programa contra PostgreSQL/Redis descartables. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DiaDeHabitosIT {
    private static final Instant ANTES = Instant.parse("2026-10-17T04:59:59Z");
    private static final UUID DESPERTAR = UUID.fromString("899a2151-e98c-4b61-a46c-b55134240d17");
    @Autowired JdbcTemplate jdbc;
    @Autowired RelojDePrueba reloj;
    @Autowired SessionRepository<? extends Session> sesiones;
    @LocalServerPort int puerto;
    private final List<UUID> usuarios = new ArrayList<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void fijarElReloj() {
        reloj.fijar(ANTES);
    }

    @AfterEach void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test void cambiaElSnapshotEnSuMedianocheYRechazaElIntentoAnterior() throws Exception {
        reloj.fijar(ANTES);
        UUID lima = participante("America/Lima");
        UUID madrid = participante("Europe/Madrid");
        UUID deAyer = registro(lima, LocalDate.of(2026, 10, 16), 10);
        UUID deHoy = registro(lima, LocalDate.of(2026, 10, 17), 11);
        registro(madrid, LocalDate.of(2026, 10, 17), 10);
        String sesion = sesionDe(sesiones, lima);
        var antes = json.readTree(get("/today/snapshot", sesion).body());
        assertThat(antes.get("fechaLocal").asText()).isEqualTo("2026-10-16");
        assertThat(antes.get("cierraEn").asText()).isEqualTo("2026-10-17T05:00:00Z");
        assertThat(antes.get("tracks").get(0).get("id").asText()).isEqualTo(deAyer.toString());
        var enMadrid = json.readTree(get("/today/snapshot", sesionDe(sesiones, madrid)).body());
        assertThat(enMadrid.get("fechaLocal").asText()).isEqualTo("2026-10-17");
        assertThat(enMadrid.get("cierraEn").asText()).isEqualTo("2026-10-17T22:00:00Z");

        reloj.fijar(Instant.parse("2026-10-17T05:00:00Z"));
        var despues = json.readTree(get("/today/snapshot", sesion).body());
        assertThat(despues.get("fechaLocal").asText()).isEqualTo("2026-10-17");
        assertThat(despues.get("tracks").get(0).get("id").asText()).isEqualTo(deHoy.toString());
        var rechazo = http.send(pedido("/" + deAyer + "/complete", sesion)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                        "{\"respuestaTexto\":null,\"calificacionProductividad\":null}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(rechazo.statusCode()).isEqualTo(409);
        assertThat(rechazo.body()).contains("ese día ya cerró");
        assertThat(jdbc.queryForObject("SELECT puntos_otorgados FROM renaser.registros_habito WHERE id = ?",
                Integer.class, deHoy)).isZero();
        assertThat(json.readTree(get("/today", sesion).body()).isArray()).isTrue();
    }

    @Test void sinSesionYSuspendidoNoAccedenAlContexto() throws Exception {
        UUID actor = participante("America/Lima");
        String sesion = sesionDe(sesiones, actor);
        assertThat(get("/today/snapshot", null).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", actor);
        assertThat(get("/today/snapshot", sesion).statusCode()).isEqualTo(403);
    }

    private UUID participante(String zona) {
        UUID id = UUID.randomUUID();
        LocalDate fecha = ANTES.atZone(ZoneId.of(zona)).toLocalDate();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba cambio de día', 'APRENDIZ', 'ACTIVO')", id, id + "@renaser.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, "
                + "programa_activado_en, timezone) VALUES (?, 10, ?, ?, ?)", id, fecha.minusDays(9),
                java.sql.Timestamp.from(ANTES.minusSeconds(864000)), zona);
        usuarios.add(id); return id;
    }
    private UUID registro(UUID actor, LocalDate fecha, int dia) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, "
                + "dia_programa, tipo_dia, es_opcional) VALUES (?, ?, ?, ?, ?, 'DISCIPLINA', false)",
                id, actor, DESPERTAR, fecha, dia);
        return id;
    }
    private HttpRequest.Builder pedido(String ruta, String sesion) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/habit-tracks" + ruta));
        if (sesion != null) builder.header("X-Auth-Token", sesion);
        return builder;
    }
    private HttpResponse<String> get(String ruta, String sesion) throws Exception {
        return http.send(pedido(ruta, sesion).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static <S extends Session> String sesionDe(SessionRepository<S> repositorio, UUID id) {
        S sesion = repositorio.createSession();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion); return sesion.getId();
    }
}

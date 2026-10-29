package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-288 (spec 014): {@code GET /api/v1/habit-tracks/day?date=} contra PostgreSQL/Redis reales (HTTP, permisos, Clock y
 * la zona real de {@code participantes_programa}).
 *
 * <p><b>ESCRITA, SIN CORRER</b> (la maquina estaba ocupada con otro backend y el emulador: nada de Testcontainers ni
 * {@code @SpringBootTest} en esa sesion). Hay que correrla con el resto del {@code verify}.
 *
 * <p>Lo que comprueba y no se puede ver con puertos de mentira:
 * <ul>
 *   <li><b>No crea tracks.</b> Una persona sin ningun registro pide un dia cerrado y sigue sin ninguno: si el pedido
 *   pasara por la red de seguridad de {@code /today}, se generarian los de hoy.</li>
 *   <li><b>Una sola lectura.</b> La cantidad de sentencias SQL de Hibernate no crece con la cantidad de habitos del
 *   dia (2 contra 10): ni N+1 ni una consulta por registro. (Cuenta solo lo que pasa por Hibernate; los adaptadores
 *   con {@code JdbcClient} no entran en esta cifra.)</li>
 *   <li><b>La ventana, en la zona de la persona</b>, con el reloj a las 22:00 del miercoles 28/10 en Lima (03:00 UTC del
 *   jueves 29), que es donde «hoy» del servidor y «hoy» de la persona no coinciden.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, DiaCerradoIT.RelojConfig.class})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DiaCerradoIT {

    /** Jueves 29/10 03:00 UTC = miercoles 28/10 22:00 en Lima. */
    private static final Instant MIERCOLES_NOCHE_EN_LIMA = Instant.parse("2026-10-29T03:00:00Z");
    private static final LocalDate LUNES_26 = LocalDate.of(2026, 10, 26);
    private static final LocalDate MARTES_27 = LocalDate.of(2026, 10, 27);
    private static final LocalDate MIERCOLES_28 = LocalDate.of(2026, 10, 28);

    @TestConfiguration static class RelojConfig {
        @Bean @Primary RelojFijo relojDiaCerrado() { return new RelojFijo(); }
    }

    static final class RelojFijo implements Clock {
        public Instant now() { return MIERCOLES_NOCHE_EN_LIMA; }
        public LocalDate today() { return MIERCOLES_NOCHE_EN_LIMA.atZone(ZoneOffset.UTC).toLocalDate(); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired SessionRepository<? extends Session> sesiones;
    @Autowired EntityManagerFactory entityManagerFactory;
    @LocalServerPort int puerto;
    private final List<UUID> usuarios = new ArrayList<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test void unDiaCerradoSinRegistrosNoCreaNingunTrack() throws Exception {
        UUID persona = participante("America/Lima", LocalDate.of(2026, 10, 19));
        String sesion = sesionDe(sesiones, persona);

        var ayer = get("?date=" + MARTES_27, sesion);
        var lunes = get("?date=" + LUNES_26, sesion);

        assertThat(ayer.statusCode()).isEqualTo(200);
        assertThat(json.readTree(ayer.body()).isArray()).isTrue();
        assertThat(json.readTree(ayer.body())).isEmpty();
        assertThat(lunes.statusCode()).isEqualTo(200);
        assertThat(registrosDe(persona)).as("el pedido no crea registros, ni de ese dia ni de hoy").isZero();
    }

    @Test void laCantidadDeConsultasNoCreceConLosHabitosDelDia() throws Exception {
        UUID persona = participante("America/Lima", LocalDate.of(2026, 10, 19));
        List<UUID> habitos = jdbc.queryForList("SELECT id FROM renaser.habitos ORDER BY id LIMIT 10", UUID.class);
        assertThat(habitos).hasSize(10);
        habitos.subList(0, 2).forEach(h -> registro(persona, h, LUNES_26));
        habitos.forEach(h -> registro(persona, h, MARTES_27));
        String sesion = sesionDe(sesiones, persona);
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        get("?date=" + LUNES_26, sesion); // calienta lo que se arma una sola vez (sesion, permisos)

        estadisticas.clear();
        var chico = get("?date=" + LUNES_26, sesion);
        long sentenciasConDos = estadisticas.getPrepareStatementCount();
        estadisticas.clear();
        var grande = get("?date=" + MARTES_27, sesion);
        long sentenciasConDiez = estadisticas.getPrepareStatementCount();

        assertThat(json.readTree(chico.body())).hasSize(2);
        assertThat(json.readTree(grande.body())).hasSize(10);
        assertThat(sentenciasConDiez).as("sentencias con 10 registros contra 2").isEqualTo(sentenciasConDos);
        assertThat(registrosDe(persona)).as("sigue habiendo solo los 12 que se sembraron").isEqualTo(12);
    }

    @Test void devuelveLaFormaDeTodayConElCatalogoYSeGuardaEnPrivado() throws Exception {
        UUID persona = participante("America/Lima", LocalDate.of(2026, 10, 19));
        registro(persona, jdbc.queryForObject("SELECT id FROM renaser.habitos ORDER BY id LIMIT 1", UUID.class),
                MARTES_27);

        var respuesta = get("?date=" + MARTES_27, sesionDe(sesiones, persona));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control")).contains("private, max-age=300");
        JsonNode primero = json.readTree(respuesta.body()).get(0);
        assertThat(primero.get("fechaEjecucion").asText()).isEqualTo("2026-10-27");
        assertThat(primero.has("tituloHabito")).isTrue();
        assertThat(primero.has("tipoHabito")).isTrue();
        assertThat(primero.has("guia")).isTrue();
        assertThat(primero.get("puntosEnJuego").isNull()).as("un dia cerrado no tiene nada en juego").isTrue();
    }

    @Test void soloLosDiasCerradosDeLaSemanaEnCursoYDesdeElDiaUno() throws Exception {
        // Hoy en Lima es el miercoles 28 (en UTC ya es el jueves 29). El Dia 1 fue el martes 27.
        UUID persona = participante("America/Lima", MARTES_27);
        String sesion = sesionDe(sesiones, persona);

        assertThat(get("?date=" + MARTES_27, sesion).statusCode()).as("ayer, Dia 1").isEqualTo(200);
        assertThat(get("?date=" + LUNES_26, sesion).statusCode()).as("antes del Dia 1").isEqualTo(400);
        assertThat(get("?date=" + MIERCOLES_28, sesion).statusCode()).as("hoy en Lima").isEqualTo(400);
        assertThat(get("?date=2026-10-29", sesion).statusCode()).as("el dia de hoy en UTC, manana en Lima")
                .isEqualTo(400);
        assertThat(get("?date=2026-11-05", sesion).statusCode()).as("futuro").isEqualTo(400);
        assertThat(get("?date=2026-10-25", sesion).statusCode()).as("otra semana").isEqualTo(400);
        assertThat(get("", sesion).statusCode()).as("sin fecha").isEqualTo(400);
        assertThat(get("?date=ayer", sesion).statusCode()).as("mal formada").isEqualTo(400);
        assertThat(get("?date=" + MARTES_27.minusDays(40), sesion).statusCode()).as("un mes atras").isEqualTo(400);
        assertThat(get("?date=" + LUNES_26, sesion).headers().firstValue("Cache-Control").orElse(""))
                .as("un rechazo no se guarda").doesNotContain("private, max-age=300");
        assertThat(registrosDe(persona)).isZero();
    }

    @Test void sinSesionYSuspendidoSon403() throws Exception {
        UUID persona = participante("America/Lima", LocalDate.of(2026, 10, 19));
        String sesion = sesionDe(sesiones, persona);

        assertThat(get("?date=" + MARTES_27, null).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", persona);
        assertThat(get("?date=" + MARTES_27, sesion).statusCode()).isEqualTo(403);
    }

    private long registrosDe(UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.registros_habito WHERE participante_id = ?",
                Long.class, persona);
    }

    /** Un Dia 1 coherente con el reloj: el dia de programa de hoy (28/10) se deriva de {@code diaUno}. */
    private UUID participante(String zona, LocalDate diaUno) {
        UUID id = UUID.randomUUID();
        int diaDePrograma = (int) (MIERCOLES_28.toEpochDay() - diaUno.toEpochDay()) + 1;
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) "
                + "VALUES (?, ?, 'Prueba dia cerrado', 'APRENDIZ', 'ACTIVO')", id, id + "@renaser.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, "
                + "programa_activado_en, timezone) VALUES (?, ?, ?, ?, ?)", id, diaDePrograma, diaUno,
                java.sql.Timestamp.from(diaUno.atStartOfDay(java.time.ZoneId.of(zona)).toInstant()), zona);
        usuarios.add(id);
        return id;
    }

    private void registro(UUID persona, UUID habito, LocalDate fecha) {
        int dia = (int) (fecha.toEpochDay() - LocalDate.of(2026, 10, 19).toEpochDay()) + 1;
        jdbc.update("INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, "
                + "dia_programa, tipo_dia, es_opcional) VALUES (?, ?, ?, ?, ?, 'DISCIPLINA', false)",
                UUID.randomUUID(), persona, habito, fecha, dia);
    }

    private HttpResponse<String> get(String consulta, String sesion) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/habit-tracks/day"
                + consulta));
        if (sesion != null) builder.header("X-Auth-Token", sesion);
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
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

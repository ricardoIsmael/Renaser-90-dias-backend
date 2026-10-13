package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.points.api.PorcentajeHabitosFinder;
import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIMNASIO de punta a punta (D-266) sobre el Tomcat real, con sesión y Postgres con V98/V99 aplicadas:
 * <ul>
 *   <li>V99 prende «ENTRENAMIENTO FÍSICO» como GIMNASIO ({@code GYM}), opcional, con foto obligatoria y horario
 *       07:00 – 23:59;</li>
 *   <li>el barrido del día genera su track, marcado opcional;</li>
 *   <li>el corte GIMNASIO cuenta los días cumplidos desde el Día 1 y lo devuelve {@code GET /ranking};</li>
 *   <li>es opcional: no ir no baja el porcentaje de hábitos.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GimnasioIT {

    private static final UUID HABITO_GIMNASIO = UUID.fromString("ec0f2b08-3a18-4ff4-8e2b-243e5220d211");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GenerarSnapshotRankingUseCase generarRanking;
    @Autowired
    private GenerarTracksDelDiaUseCase generarTracks;
    @Autowired
    private PorcentajeHabitosFinder porcentajeHabitos;
    @Autowired
    private Clock reloj;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();

    private LocalDate hoy;
    private UUID ana;
    private UUID beto;
    private UUID carla;

    @BeforeEach
    void seed() {
        hoy = reloj.now().atZone(LIMA).toLocalDate();
        ana = aprendiz("Ana Constante");
        beto = aprendiz("Beto Ocasional");
        carla = aprendiz("Carla Todavía No");
        // Ana fue tres días (uno sigue PENDIENTE hoy: no cuenta), Beto uno, Carla ninguno.
        registro(ana, HABITO_GIMNASIO, hoy.minusDays(3), "COMPLETADO", true);
        registro(ana, HABITO_GIMNASIO, hoy.minusDays(2), "COMPLETADO", true);
        registro(ana, HABITO_GIMNASIO, hoy.minusDays(1), "COMPLETADO", true);
        registro(ana, HABITO_GIMNASIO, hoy, "PENDIENTE", true);
        registro(beto, HABITO_GIMNASIO, hoy.minusDays(1), "COMPLETADO", true);
        // Un día FALLIDO no suma.
        registro(beto, HABITO_GIMNASIO, hoy.minusDays(2), "FALLIDO", true);
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.ranking_aprendices WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // Algún oyente asíncrono pudo dejar una fila que la referencia: la base de pruebas es descartable.
            }
        });
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    @Test
    @DisplayName("V99 prende GIMNASIO para todos: GYM, activo, opcional, foto obligatoria y horario 07:00 – 23:59")
    void v99PrendeElHabito() {
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT titulo, clave_sistema, activo, es_opcional, exigencia_evidencia::text AS evidencia
                FROM renaser.habitos WHERE id = ?
                """, HABITO_GIMNASIO);
        assertThat(fila).containsEntry("titulo", "GIMNASIO").containsEntry("clave_sistema", "GYM")
                .containsEntry("activo", true).containsEntry("es_opcional", true).containsEntry("evidencia", "OBLIGATORIA");
        assertThat(jdbc.queryForList("SELECT hora_disparo::text || ' – ' || hora_limite::text FROM renaser.horarios_habito "
                + "WHERE habito_id = ?", String.class, HABITO_GIMNASIO)).containsExactly("07:00:00 – 23:59:00");
        assertThat(jdbc.queryForList("SELECT count(*) FROM renaser.audiencias_habito WHERE habito_id = ?",
                Integer.class, HABITO_GIMNASIO)).containsExactly(0);
    }

    @Test
    @DisplayName("el barrido del día genera el track de gimnasio, marcado opcional")
    void elBarridoGeneraElTrackOpcional() {
        UUID dani = aprendiz("Dani Nueva");

        generarTracks.generarDiaCompletoEnSuZona(UserId.of(dani));

        assertThat(jdbc.queryForList(
                "SELECT es_opcional FROM renaser.registros_habito WHERE participante_id = ? AND habito_id = ? AND fecha_ejecucion = ?",
                Boolean.class, dani, HABITO_GIMNASIO, hoy)).containsExactly(true);
    }

    @Test
    @DisplayName("el corte GIMNASIO cuenta los días cumplidos desde el Día 1 y GET /ranking lo devuelve, con quien no fue al fondo")
    void rankingDeGimnasio() throws Exception {
        generarRanking.generar(TipoRanking.GIMNASIO, hoy);

        JsonNode tabla = json.readTree(pedir("/api/v1/ranking?fecha=" + hoy, sesionDe(beto)).body()).get("gimnasio");
        List<String> ids = new ArrayList<>();
        tabla.forEach(e -> ids.add(e.get("participanteId").asText()));
        assertThat(ids).containsSubsequence(ana.toString(), beto.toString(), carla.toString());
        assertThat(puntajeDe(tabla, ana)).isEqualByComparingTo("3");
        assertThat(puntajeDe(tabla, beto)).isEqualByComparingTo("1");
        assertThat(puntajeDe(tabla, carla)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("el hábito es opcional: no ir al gimnasio no baja el porcentaje de hábitos")
    void opcionalNoRestaEnElPromedio() {
        UUID meditar = habitoComun("Meditar");
        registro(carla, meditar, hoy, "COMPLETADO", false);
        registro(carla, HABITO_GIMNASIO, hoy, "PENDIENTE", true);

        BigDecimal porcentaje = porcentajeHabitos.porcentajePorParticipante(List.of(UserId.of(carla)), hoy)
                .get(UserId.of(carla));

        assertThat(porcentaje).isEqualByComparingTo("100");
    }

    private static BigDecimal puntajeDe(JsonNode tabla, UUID quien) {
        for (JsonNode e : tabla) {
            if (e.get("participanteId").asText().equals(quien.toString())) {
                return e.get("puntaje").decimalValue();
            }
        }
        throw new AssertionError("no figura en el ranking: " + quien);
    }

    private HttpResponse<byte[]> pedir(String ruta, String sesion) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", sesion).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
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

    /** Aprendiz activo en su Día 12 (fecha de inicio coherente con el día: hoy − 11, regla 03). */
    private UUID aprendiz(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, 'APRENDIZ', 'ACTIVO')
                """, id, id + "@renaser.test", nombre);
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone)
                VALUES (?, 12, ?, now(), 'America/Lima')
                """, id, hoy.minusDays(11));
        usuarios.add(id);
        return id;
    }

    private UUID habitoComun(String titulo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                VALUES (?, ?, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                """, id, titulo + " " + id);
        habitos.add(id);
        return id;
    }

    private void registro(UUID quien, UUID habito, LocalDate fecha, String estado, boolean opcional) {
        int dia = 12 - (int) (hoy.toEpochDay() - fecha.toEpochDay());
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                      tipo_dia, es_opcional, estado, completado_en)
                VALUES (?, ?, ?, ?, ?, 'TODOS', ?, CAST(? AS renaser.estado_registro),
                        CASE WHEN ? = 'COMPLETADO' THEN now() END)
                """, UUID.randomUUID(), quien, habito, fecha, dia, opcional, estado, estado);
    }
}

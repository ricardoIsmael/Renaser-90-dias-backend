package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.points.api.PorcentajeHabitosFinder;
import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
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
 * KILÓMETROS DIARIOS de punta a punta (D-226) sobre el Tomcat real, con sesión, Spring Security y
 * Postgres con V84/V85 aplicadas:
 * <ul>
 *   <li>V84 prende el hábito {@code DAILY_KM} para todos, opcional y con captura obligatoria;</li>
 *   <li>completar con km guarda el número (dos decimales, origen MANUAL); sin km, con km negativos o
 *       en un hábito que no mide nada es un 400; en el registro de otro, un 403;</li>
 *   <li>{@code GET /habit-tracks/today} trae la unidad y el total acumulado;</li>
 *   <li>el corte del ranking KILOMETROS suma desde el Día 1 y lo devuelve {@code GET /ranking};</li>
 *   <li>el hábito es opcional: no completarlo no baja el porcentaje de hábitos.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class KilometrosDiariosIT {

    private static final UUID HABITO_KM = UUID.fromString("ea87fdec-d4c1-4c4f-9e61-1557bc7255d1");
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

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();

    /** El "hoy" del padrón, como lo resuelve el servidor para alguien de Lima. */
    private LocalDate hoy;
    private UUID ana;
    private UUID beto;
    private UUID kmDeAnaHoy;
    private UUID kmDeBetoHoy;

    @BeforeEach
    void seed() {
        hoy = LocalDate.now(LIMA);
        ana = aprendiz("Ana Corredora");
        beto = aprendiz("Beto Caminante");
        // Ana ya registró 3,50 km anteayer y 2,25 ayer: el total arranca en 5,75.
        registroKm(ana, hoy.minusDays(2), "COMPLETADO", new BigDecimal("3.50"));
        registroKm(ana, hoy.minusDays(1), "COMPLETADO", new BigDecimal("2.25"));
        kmDeAnaHoy = registroKm(ana, hoy, "PENDIENTE", null);
        kmDeBetoHoy = registroKm(beto, hoy, "PENDIENTE", null);
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.ranking_aprendices WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // Algún oyente asíncrono (avisos, puntos) pudo dejar una fila que la referencia sin
                // cascada: queda como un aprendiz más de la base de pruebas, igual que en otras IT.
            }
        });
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    @Test
    @DisplayName("V84 prende KILÓMETROS DIARIOS para todos: DAILY_KM, activo, opcional y con captura obligatoria")
    void v84PrendeElHabito() {
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT clave_sistema, activo, es_opcional, exigencia_evidencia::text AS evidencia FROM renaser.habitos WHERE id = ?",
                HABITO_KM);
        assertThat(fila).containsEntry("clave_sistema", "DAILY_KM").containsEntry("activo", true)
                .containsEntry("es_opcional", true).containsEntry("evidencia", "OBLIGATORIA");
    }

    @Test
    @DisplayName("el barrido del día genera el track de km, marcado opcional")
    void elBarridoGeneraElTrackOpcional() {
        UUID carla = aprendiz("Carla Nueva");

        generarTracks.generarDiaCompletoEnSuZona(UserId.of(carla));

        assertThat(jdbc.queryForList(
                "SELECT es_opcional FROM renaser.registros_habito WHERE participante_id = ? AND habito_id = ? AND fecha_ejecucion = ?",
                Boolean.class, carla, HABITO_KM, hoy)).containsExactly(true);
    }

    @Test
    @DisplayName("completar con km guarda el número y el total del día lo suma; la agenda lo muestra")
    void completarConKm() throws Exception {
        HttpResponse<byte[]> respuesta = completar(kmDeAnaHoy, "{\"valorMedido\": 4.126}", sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(cuerpo.get("valorMedido").decimalValue()).isEqualByComparingTo("4.13");
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT valor_medido, origen_medicion FROM renaser.registros_habito WHERE id = ?", kmDeAnaHoy);
        assertThat((BigDecimal) fila.get("valor_medido")).isEqualByComparingTo("4.13");
        assertThat(fila).containsEntry("origen_medicion", "MANUAL");

        JsonNode trackDeKm = trackDeHoy(ana, kmDeAnaHoy);
        assertThat(trackDeKm.at("/medicion/unidad").asText()).isEqualTo("KILOMETROS");
        assertThat(trackDeKm.at("/medicion/valorDelDia").decimalValue()).isEqualByComparingTo("4.13");
        assertThat(trackDeKm.at("/medicion/total").decimalValue()).isEqualByComparingTo("9.88");
    }

    @Test
    @DisplayName("antes de completar, la agenda trae el total acumulado para mostrarlo en la pantalla de km")
    void laAgendaTraeElTotalAntesDeCompletar() throws Exception {
        JsonNode trackDeKm = trackDeHoy(ana, kmDeAnaHoy);

        assertThat(trackDeKm.at("/medicion/valorDelDia").isNull()).isTrue();
        assertThat(trackDeKm.at("/medicion/total").decimalValue()).isEqualByComparingTo("5.75");
    }

    @Test
    @DisplayName("sin km, con km negativos o con cero es un 400, y el registro sigue pendiente")
    void sinKmEs400() throws Exception {
        String sesion = sesionDe(ana);
        HttpResponse<byte[]> sinKm = completar(kmDeAnaHoy, "{}", sesion);
        assertThat(sinKm.statusCode()).isEqualTo(400);
        assertThat(new String(sinKm.body())).contains("km");
        assertThat(completar(kmDeAnaHoy, "{\"valorMedido\": -2}", sesion).statusCode()).isEqualTo(400);
        assertThat(completar(kmDeAnaHoy, "{\"valorMedido\": 0}", sesion).statusCode()).isEqualTo(400);
        assertThat(completar(kmDeAnaHoy, "{\"valorMedido\": 250}", sesion).statusCode()).isEqualTo(400);

        assertThat(jdbc.queryForObject("SELECT estado::text FROM renaser.registros_habito WHERE id = ?", String.class,
                kmDeAnaHoy)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("un número en un hábito que no mide nada es un 400")
    void numeroEnOtroHabitoEs400() throws Exception {
        UUID meditar = habitoComun("Meditar");
        UUID registro = registro(ana, meditar, hoy, "PENDIENTE", false, null);

        assertThat(completar(registro, "{\"valorMedido\": 3}", sesionDe(ana)).statusCode()).isEqualTo(400);
        assertThat(completar(registro, "{}", sesionDe(ana)).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("otro aprendiz no puede registrar km en el registro de alguien más: 403 y nada cambia")
    void otroAprendizNoRegistraKmAjenos() throws Exception {
        assertThat(completar(kmDeAnaHoy, "{\"valorMedido\": 5}", sesionDe(beto)).statusCode()).isEqualTo(403);
        assertThat(completar(kmDeAnaHoy, "{\"valorMedido\": 5}", null).statusCode()).isIn(401, 403);

        assertThat(jdbc.queryForObject("SELECT valor_medido FROM renaser.registros_habito WHERE id = ?",
                BigDecimal.class, kmDeAnaHoy)).isNull();
    }

    @Test
    @DisplayName("el corte KILOMETROS suma desde el Día 1 y GET /ranking lo devuelve, con quien no corrió al fondo")
    void rankingDeKilometros() throws Exception {
        assertThat(completar(kmDeAnaHoy, "{\"valorMedido\": 1.25}", sesionDe(ana)).statusCode()).isEqualTo(200);
        assertThat(completar(kmDeBetoHoy, "{\"valorMedido\": 3}", sesionDe(beto)).statusCode()).isEqualTo(200);

        generarRanking.generar(TipoRanking.KILOMETROS, hoy);

        JsonNode tabla = json.readTree(pedir("/api/v1/ranking?fecha=" + hoy, sesionDe(beto)).body()).get("kilometros");
        List<String> ids = new ArrayList<>();
        tabla.forEach(e -> ids.add(e.get("participanteId").asText()));
        assertThat(ids).containsSubsequence(ana.toString(), beto.toString());
        assertThat(puntajeDe(tabla, ana)).isEqualByComparingTo("7.00");
        assertThat(puntajeDe(tabla, beto)).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("el hábito es opcional: no completarlo no baja el porcentaje de hábitos")
    void opcionalNoRestaEnElPromedio() {
        UUID meditar = habitoComun("Meditar");
        registro(beto, meditar, hoy, "COMPLETADO", false, null);

        BigDecimal porcentaje = porcentajeHabitos.porcentajePorParticipante(List.of(UserId.of(beto)), hoy)
                .get(UserId.of(beto));

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

    private JsonNode trackDeHoy(UUID quien, UUID registro) throws Exception {
        HttpResponse<byte[]> respuesta = pedir("/api/v1/habit-tracks/today", sesionDe(quien));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        for (JsonNode track : json.readTree(respuesta.body())) {
            if (track.get("id").asText().equals(registro.toString())) {
                return track;
            }
        }
        throw new AssertionError("el track no está en la agenda de hoy: " + registro);
    }

    private HttpResponse<byte[]> completar(UUID registro, String cuerpo, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/habit-tracks/" + registro + "/complete"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofByteArray());
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

    private UUID registroKm(UUID quien, LocalDate fecha, String estado, BigDecimal km) {
        return registro(quien, HABITO_KM, fecha, estado, true, km);
    }

    private UUID registro(UUID quien, UUID habito, LocalDate fecha, String estado, boolean opcional, BigDecimal km) {
        UUID id = UUID.randomUUID();
        int dia = 12 - (int) (hoy.toEpochDay() - fecha.toEpochDay());
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                      tipo_dia, es_opcional, estado, valor_medido, origen_medicion,
                                                      completado_en)
                VALUES (?, ?, ?, ?, ?, 'TODOS', ?, CAST(? AS renaser.estado_registro), ?, ?,
                        CASE WHEN ? = 'COMPLETADO' THEN now() END)
                """, id, quien, habito, fecha, dia, opcional, estado, km, km == null ? null : "MANUAL", estado);
        return id;
    }
}

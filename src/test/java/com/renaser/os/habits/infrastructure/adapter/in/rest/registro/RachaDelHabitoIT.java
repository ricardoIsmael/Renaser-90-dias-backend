package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
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
import org.springframework.dao.DataAccessException;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-254 de punta a punta: {@code GET /api/v1/habit-tracks/today} trae la racha de cada habito
 * ({@code rachaDias}), sobre el Tomcat real, con sesion, Spring Security y Postgres.
 *
 * <p><b>El reloj esta a las 03:00 UTC del 5/10</b>: en Lima todavia son las 22:00 del domingo 4. Es la
 * franja en que la fecha del servidor y la del aprendiz no coinciden (regla 02 §3, E-91/E-105). Si la
 * racha mirara la fecha UTC, el pendiente del 4 seria un dia terminado sin cumplir y todas las rachas
 * de esta prueba darian 0.
 *
 * <p>Fixture coherente (regla 03): Ana esta en su Dia 12 y arranco el 23/09 = hoy − 11.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, RachaDelHabitoIT.RelojDeMadrugadaUtc.class})
class RachaDelHabitoIT {

    private static final Instant AHORA = Instant.parse("2026-10-05T03:00:00Z");
    /** El hoy de Lima a las 03:00 UTC del 5. */
    private static final LocalDate HOY = LocalDate.of(2026, 10, 4);
    private static final int DIA_PROGRAMA = 12;
    private static final LocalDate INICIO = HOY.minusDays(DIA_PROGRAMA - 1L);

    /** Los campos que la app ya instalada lee hoy (`trackDelDiaSchema`): ninguno puede faltar ni cambiar de nombre. */
    private static final List<String> CAMPOS_DE_LA_APP_VIEJA = List.of("id", "habitoId", "fechaEjecucion",
            "diaPrograma", "tipoDia", "esOpcional", "estado", "puntosOtorgados", "completadoEn", "respuestaTexto",
            "calificacionProductividad", "guia", "tituloHabito", "tipoHabito", "horaDisparo", "horaLimite",
            "puntosEnJuego", "puntosMaximos", "plazoEvidencia", "tieneEvidencia", "medicion");

    @TestConfiguration
    static class RelojDeMadrugadaUtc {
        @Bean
        @Primary
        Clock relojFijo() {
            return FixedClock.at(AHORA);
        }
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();

    private UUID ana;
    private UUID beto;
    private UUID meditar;
    private UUID leer;
    private UUID caminar;
    private UUID diario;
    private UUID enPausa;
    private UUID conOpcional;

    @BeforeEach
    void seed() {
        ana = aprendiz("Ana Constante");
        beto = aprendiz("Beto Vecino");
        meditar = habito("Meditar");
        leer = habito("Leer");
        caminar = habito("Caminar");
        diario = habito("Diario");

        // Meditar (1A): hoy pendiente, ayer cumplido, anteayer NO le tocaba (sin fila), el 1 cumplido,
        // el 30/09 vencido. Racha = 2: el pendiente de hoy no corta y el dia sin fila tampoco.
        registro(ana, meditar, HOY, "PENDIENTE");
        registro(ana, meditar, HOY.minusDays(1), "COMPLETADO");
        registro(ana, meditar, HOY.minusDays(3), "COMPLETADO");
        registro(ana, meditar, HOY.minusDays(4), "EXPIRADO");
        // Leer: hoy cumplido, ayer vencido. Racha = 1.
        registro(ana, leer, HOY, "COMPLETADO");
        registro(ana, leer, HOY.minusDays(1), "EXPIRADO");
        // Caminar: habito nuevo, solo hoy y pendiente. Racha = 0.
        registro(ana, caminar, HOY, "PENDIENTE");
        // Diario (2A + S-4): cumplido todos los dias desde el inicio salvo tres en pausa (sin fila), y un
        // registro suelto ANTERIOR al inicio que no puede contar. Racha = 12 − 3 = 9.
        for (LocalDate fecha = INICIO; !fecha.isAfter(HOY); fecha = fecha.plusDays(1)) {
            boolean enPausa = !fecha.isBefore(HOY.minusDays(6)) && !fecha.isAfter(HOY.minusDays(4));
            if (!enPausa) {
                registro(ana, diario, fecha, "COMPLETADO");
            }
        }
        registro(ana, diario, INICIO.minusDays(1), "COMPLETADO");

        // Beto tiene el MISMO habito Meditar, con otra historia: su racha no se mezcla con la de Ana.
        for (int i = 0; i < 5; i++) {
            registro(beto, meditar, HOY.minusDays(i), "COMPLETADO");
        }

        // Decision 2 del dueño: sin track de hoy. En pausa desde hoy (sin fila hoy): cumplido los 3 dias anteriores.
        // Racha congelada = 3.
        enPausa = habito("En pausa");
        for (int i = 1; i <= 3; i++) {
            registro(ana, enPausa, HOY.minusDays(i), "COMPLETADO");
        }
        registro(ana, enPausa, HOY.minusDays(4), "EXPIRADO");
        // Decision 1 del dueño: un dia opcional sin cumplir (ciclo de intoxicacion) no corta ni suma, y hoy no le
        // toca. Cumplido ayer, opcional vencido anteayer, cumplido los dos de antes: 3 (antes de la decision, 1).
        conOpcional = habito("Con opcional");
        registro(ana, conOpcional, HOY.minusDays(1), "COMPLETADO");
        registro(ana, conOpcional, HOY.minusDays(2), "EXPIRADO", true);
        registro(ana, conOpcional, HOY.minusDays(3), "COMPLETADO");
        registro(ana, conOpcional, HOY.minusDays(4), "COMPLETADO");
        registro(ana, conOpcional, HOY.minusDays(5), "EXPIRADO");
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // Un oyente asincrono pudo dejar una fila que la referencia sin cascada (como en KilometrosDiariosIT).
            }
        });
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    @Test
    @DisplayName("la duena ve la racha real de cada habito, con el hoy de Lima a las 03:00 UTC")
    void laDuenaVeSuRacha() throws Exception {
        Map<UUID, JsonNode> tracks = tracksDeHoy(ana);

        assertThat(tracks).containsOnlyKeys(meditar, leer, caminar, diario);
        assertThat(tracks.values()).allSatisfy(t -> assertThat(t.get("fechaEjecucion").asText()).isEqualTo("2026-10-04"));
        assertThat(tracks.get(meditar).get("rachaDias").asInt()).isEqualTo(2);
        assertThat(tracks.get(leer).get("rachaDias").asInt()).isEqualTo(1);
        assertThat(tracks.get(caminar).get("rachaDias").asInt()).isZero();
        assertThat(tracks.get(diario).get("rachaDias").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("otro aprendiz no ve los tracks ajenos, y su racha del mismo habito es la suya")
    void otroNoVeLoAjeno() throws Exception {
        Map<UUID, JsonNode> deBeto = tracksDeHoy(beto);

        assertThat(deBeto).containsOnlyKeys(meditar);
        assertThat(deBeto.get(meditar).get("rachaDias").asInt()).isEqualTo(5);
        List<String> idsDeAna = new ArrayList<>();
        tracksDeHoy(ana).values().forEach(t -> idsDeAna.add(t.get("id").asText()));
        assertThat(deBeto.get(meditar).get("id").asText()).isNotIn(idsDeAna);
    }

    @Test
    @DisplayName("sin sesion no hay tracks de nadie")
    void sinSesion() throws Exception {
        HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/habit-tracks/today")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(respuesta.statusCode()).isIn(401, 403);
    }

    @Test
    @DisplayName("la app vieja sigue funcionando: todos los campos de antes estan, y rachaDias es solo uno mas")
    void compatibleConLaAppVieja() throws Exception {
        JsonNode track = tracksDeHoy(ana).get(meditar);

        List<String> campos = new ArrayList<>();
        track.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsAll(CAMPOS_DE_LA_APP_VIEJA);
        assertThat(campos).hasSize(CAMPOS_DE_LA_APP_VIEJA.size() + 1).contains("rachaDias");
        assertThat(track.get("rachaDias").isInt()).isTrue();
    }

    // ---- GET /api/v1/habits: la racha congelada del habito sin track de hoy (D-254, decision 2) ----

    /** Los campos que el APK publicado lee de cada habito de {@code GET /api/v1/habits} (`habitoCatalogoSchema`). */
    private static final List<String> CAMPOS_DEL_CATALOGO = List.of("id", "title", "description", "habitType",
            "category", "evidenceRequirement", "isOptional", "isSystemHabit", "isDeactivatable", "systemKey",
            "iconKey", "activeWeekdays", "unlockDay", "daysUntilUnlock", "locked");

    @Test
    @DisplayName("GET /habits: la racha congelada del que hoy no tiene track (pausa, opcional) y la misma del track")
    void elCatalogoTraeLaRachaCongelada() throws Exception {
        Map<UUID, JsonNode> deAna = misHabitos(ana);

        assertThat(deAna.get(enPausa).get("rachaDias").asInt()).as("en pausa: congelada").isEqualTo(3);
        assertThat(deAna.get(conOpcional).get("rachaDias").asInt()).as("el opcional sin cumplir no corta").isEqualTo(3);
        assertThat(deAna.get(meditar).get("rachaDias").asInt())
                .isEqualTo(tracksDeHoy(ana).get(meditar).get("rachaDias").asInt()).isEqualTo(2);
        assertThat(deAna.get(diario).get("rachaDias").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("GET /habits: la racha de cada uno es la suya, aunque el habito sea el mismo")
    void cadaUnoVeLaSuya() throws Exception {
        Map<UUID, JsonNode> deBeto = misHabitos(beto);

        assertThat(deBeto.get(meditar).get("rachaDias").asInt()).isEqualTo(5);
        assertThat(deBeto.get(enPausa).get("rachaDias").asInt()).as("Beto no tiene historia de este").isZero();
    }

    @Test
    @DisplayName("GET /habits: la app vieja sigue funcionando, todos los campos de antes y rachaDias es solo uno mas")
    void elCatalogoEsCompatible() throws Exception {
        JsonNode habito = misHabitos(ana).get(enPausa);

        List<String> campos = new ArrayList<>();
        habito.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsAll(CAMPOS_DEL_CATALOGO);
        assertThat(campos).hasSize(CAMPOS_DEL_CATALOGO.size() + 1).contains("rachaDias");
    }

    @Test
    @DisplayName("GET /habits sin sesion: nada")
    void elCatalogoSinSesion() throws Exception {
        HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/habits")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(respuesta.statusCode()).isIn(401, 403);
    }

    private Map<UUID, JsonNode> misHabitos(UUID quien) throws Exception {
        HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/habits"))
                .header("X-Auth-Token", sesionDe(quien)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        Map<UUID, JsonNode> porHabito = new HashMap<>();
        json.readTree(respuesta.body()).forEach(h -> porHabito.put(UUID.fromString(h.get("id").asText()), h));
        return porHabito;
    }

    private Map<UUID, JsonNode> tracksDeHoy(UUID quien) throws Exception {
        HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/habit-tracks/today"))
                .header("X-Auth-Token", sesionDe(quien)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        Map<UUID, JsonNode> porHabito = new HashMap<>();
        json.readTree(respuesta.body()).forEach(t -> porHabito.put(UUID.fromString(t.get("habitoId").asText()), t));
        return porHabito;
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

    private UUID aprendiz(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, 'APRENDIZ', 'ACTIVO')
                """, id, id + "@renaser.test", nombre);
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone)
                VALUES (?, ?, ?, ?, 'America/Lima')
                """, id, DIA_PROGRAMA, INICIO, java.sql.Timestamp.from(AHORA.minusSeconds(86_400L * 15)));
        usuarios.add(id);
        return id;
    }

    private UUID habito(String titulo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.habitos (id, titulo, categoria_clave)
                VALUES (?, ?, (SELECT clave FROM renaser.categorias_habito LIMIT 1))
                """, id, titulo + " " + id);
        habitos.add(id);
        return id;
    }

    /** {@code dia_programa} coherente con la fecha: el inicio es el Dia 1, y antes del inicio es el 0. */
    private void registro(UUID quien, UUID habito, LocalDate fecha, String estado) {
        registro(quien, habito, fecha, estado, false);
    }

    private void registro(UUID quien, UUID habito, LocalDate fecha, String estado, boolean opcional) {
        int dia = Math.max(0, (int) (fecha.toEpochDay() - INICIO.toEpochDay()) + 1);
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                      tipo_dia, es_opcional, estado, completado_en)
                VALUES (?, ?, ?, ?, ?, 'TODOS', ?, CAST(? AS renaser.estado_registro),
                        CASE WHEN ? = 'COMPLETADO' THEN now() END)
                """, UUID.randomUUID(), quien, habito, fecha, dia, opcional, estado, estado);
    }
}

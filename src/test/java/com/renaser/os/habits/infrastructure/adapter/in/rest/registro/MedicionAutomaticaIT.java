package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.application.ports.in.ranking.GenerarSnapshotRankingUseCase;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.Clock;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los pasos del teléfono cierran «Kilómetros diarios» (spec 037, D-310) de punta a punta sobre el Tomcat real, con sesión,
 * Spring Security y Postgres con V161 aplicada:
 * <ul>
 *   <li>con la meta alcanzada el hábito se cierra SIN captura, y la base guarda km, pasos, fuente, dispositivo y hora;</li>
 *   <li>es idempotente; lo dudoso entra marcado; faltar a la meta, el día cerrado, otro dueño y la captura previa se rechazan;</li>
 *   <li>Administración fija la meta y rige desde ya; la agenda de hoy la trae junto al origen del día;</li>
 *   <li>el ranking de km marca a quien tiene días automáticos, y la base no deja una lectura a medias.</li>
 * </ul>
 * La IT no supone una base vacía: siembra sus propias personas y limpia lo que siembra (regla 03).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MedicionAutomaticaIT {

    private static final UUID HABITO_KM = UUID.fromString("ea87fdec-d4c1-4c4f-9e61-1557bc7255d1");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String LECTURA = """
            {"kilometros": %s, "pasos": %s, "fuente": "Galaxy Watch", "dispositivo": "RELOJ", "leidoEn": "%s"}""";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GenerarSnapshotRankingUseCase generarRanking;
    @Autowired
    private Clock reloj;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();

    private LocalDate hoy;
    private UUID ana;
    private UUID beto;
    private UUID admin;
    private UUID kmDeAnaHoy;

    @BeforeEach
    void seed() {
        hoy = reloj.now().atZone(LIMA).toLocalDate();
        ana = persona("Ana Caminante", "APRENDIZ");
        beto = persona("Beto Corredor", "APRENDIZ");
        admin = persona("Admin Metas", "ADMIN");
        kmDeAnaHoy = registroKm(ana, hoy, "PENDIENTE", null);
    }

    @AfterEach
    void limpiar() {
        // La meta es del hábito, que es de todos: cada prueba la deja como la encontró (sin meta fijada).
        jdbc.update("UPDATE renaser.habitos SET meta_medicion = NULL WHERE id = ?", HABITO_KM);
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.ranking_aprendices WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // Un oyente asíncrono (avisos, puntos) pudo dejar una fila que la referencia: queda como una persona más de la base de pruebas.
            }
        });
        habitos.forEach(id -> jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id));
    }

    // spec 037 FR-008, FR-011
    @Test
    @DisplayName("con la meta alcanzada cierra el hábito sin captura y la base guarda la lectura entera")
    void cierraYGuardaLaLectura() throws Exception {
        HttpResponse<byte[]> respuesta = registrar(kmDeAnaHoy, "5.404", 7120, sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(cuerpo.get("valorMedido").decimalValue()).isEqualByComparingTo("5.40");
        assertThat(cuerpo.at("/medicion/origen").asText()).isEqualTo("HEALTH_CONNECT");
        assertThat(cuerpo.at("/medicion/fuente").asText()).isEqualTo("Galaxy Watch");
        assertThat(cuerpo.at("/medicion/porRevisar").asBoolean()).isFalse();
        Map<String, Object> fila = jdbc.queryForMap("""
                SELECT estado::text AS estado, valor_medido, origen_medicion, pasos_medidos, fuente_medicion,
                       dispositivo_medicion, medicion_leida_en, medicion_por_revisar
                FROM renaser.registros_habito WHERE id = ?""", kmDeAnaHoy);
        assertThat(fila).containsEntry("estado", "COMPLETADO").containsEntry("origen_medicion", "HEALTH_CONNECT")
                .containsEntry("pasos_medidos", 7120).containsEntry("fuente_medicion", "Galaxy Watch")
                .containsEntry("dispositivo_medicion", "RELOJ").containsEntry("medicion_por_revisar", false);
        assertThat((BigDecimal) fila.get("valor_medido")).isEqualByComparingTo("5.40");
        assertThat(fila.get("medicion_leida_en")).isNotNull();
    }

    // spec 037 FR-009
    @Test
    @DisplayName("es idempotente: reenviar (aunque con otro número) devuelve lo ya cerrado, sin pagar ni cambiar nada")
    void esIdempotente() throws Exception {
        String sesion = sesionDe(ana);
        assertThat(registrar(kmDeAnaHoy, "5.4", 7120, sesion).statusCode()).isEqualTo(200);
        Map<String, Object> antes = jdbc.queryForMap(
                "SELECT completado_en, puntos_otorgados, valor_medido FROM renaser.registros_habito WHERE id = ?", kmDeAnaHoy);

        HttpResponse<byte[]> segunda = registrar(kmDeAnaHoy, "9.9", 12000, sesion);

        assertThat(segunda.statusCode()).isEqualTo(200);
        assertThat(json.readTree(segunda.body()).get("valorMedido").decimalValue()).isEqualByComparingTo("5.40");
        assertThat(jdbc.queryForMap(
                "SELECT completado_en, puntos_otorgados, valor_medido FROM renaser.registros_habito WHERE id = ?",
                kmDeAnaHoy)).isEqualTo(antes);
    }

    @Test
    @DisplayName("faltar a la meta es un 400 que dice cuál es, y el registro sigue pendiente")
    void faltaLaMeta() throws Exception {
        HttpResponse<byte[]> respuesta = registrar(kmDeAnaHoy, "3.2", 4200, sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(texto(respuesta)).contains("Todavía no llegas a tu meta de 5 km.");
        assertThat(estadoDe(kmDeAnaHoy)).isEqualTo("PENDIENTE");
    }

    // spec 037 FR-012
    @Test
    @DisplayName("la meta que fija Administración rige desde ya; volver a la del programa la quita")
    void laMetaDeAdministracion() throws Exception {
        String sesionAdmin = sesionDe(admin);
        HttpResponse<byte[]> fijada = enviar("PUT", "/api/v1/admin/habits/" + HABITO_KM + "/measurement-goal",
                "{\"meta\": 3}", sesionAdmin);
        assertThat(fijada.statusCode()).isEqualTo(200);
        JsonNode vista = json.readTree(fijada.body());
        assertThat(vista.get("meta").decimalValue()).isEqualByComparingTo("3");
        assertThat(vista.get("esLaPorDefecto").asBoolean()).isFalse();

        assertThat(registrar(kmDeAnaHoy, "3.2", 4200, sesionDe(ana)).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Administración ve la meta (5 km sin fijar), no la sale de 0,5 a 50, y solo ella; un hábito que no mide es 404")
    void laMetaSeVeYSeValida() throws Exception {
        String ruta = "/api/v1/admin/habits/" + HABITO_KM + "/measurement-goal";

        HttpResponse<byte[]> vista = enviar("GET", ruta, null, sesionDe(admin));
        assertThat(vista.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(vista.body());
        assertThat(cuerpo.get("unidad").asText()).isEqualTo("KILOMETROS");
        assertThat(cuerpo.get("meta").decimalValue()).isEqualByComparingTo("5");
        assertThat(cuerpo.get("esLaPorDefecto").asBoolean()).isTrue();

        assertThat(enviar("PUT", ruta, "{\"meta\": 100}", sesionDe(admin)).statusCode()).isEqualTo(400);
        assertThat(enviar("PUT", ruta, "{\"meta\": 0.2}", sesionDe(admin)).statusCode()).isEqualTo(400);
        assertThat(enviar("GET", ruta, null, sesionDe(ana)).statusCode()).isEqualTo(403);
        assertThat(enviar("PUT", ruta, "{\"meta\": 4}", sesionDe(ana)).statusCode()).isEqualTo(403);

        UUID meditar = habitoComun("Meditar");
        assertThat(enviar("GET", "/api/v1/admin/habits/" + meditar + "/measurement-goal", null, sesionDe(admin))
                .statusCode()).isEqualTo(404);
    }

    // spec 037 FR-010
    @Test
    @DisplayName("lo dudoso (más de 30 km) se acepta y queda por revisar para el mentor")
    void loDudosoQuedaMarcado() throws Exception {
        assertThat(registrar(kmDeAnaHoy, "35", 46000, sesionDe(ana)).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT medicion_por_revisar FROM renaser.registros_habito WHERE id = ?",
                Boolean.class, kmDeAnaHoy)).isTrue();
    }

    @Test
    @DisplayName("otra persona no registra por ti: 403 y nada cambia")
    void otraPersonaNo() throws Exception {
        assertThat(registrar(kmDeAnaHoy, "6", 8000, sesionDe(beto)).statusCode()).isEqualTo(403);
        assertThat(estadoDe(kmDeAnaHoy)).isEqualTo("PENDIENTE");
    }

    // spec 037 FR-009
    @Test
    @DisplayName("un hábito ya cumplido con la captura es un 409 y no se pisa")
    void cumplidoConLaCaptura() throws Exception {
        jdbc.update("DELETE FROM renaser.registros_habito WHERE id = ?", kmDeAnaHoy);
        UUID cumplido = registroKm(ana, hoy, "COMPLETADO", new BigDecimal("4.00"));

        HttpResponse<byte[]> respuesta = registrar(cumplido, "6", 8000, sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(409);
        assertThat(texto(respuesta)).contains("con tu captura");
        assertThat(jdbc.queryForObject("SELECT origen_medicion FROM renaser.registros_habito WHERE id = ?", String.class,
                cumplido)).isEqualTo("MANUAL");
    }

    // spec 037 FR-008
    @Test
    @DisplayName("el hábito de un día que ya terminó no se cierra: 409 (D-259), y una lectura de otro día es 400")
    void diaCerradoYLecturaDeOtroDia() throws Exception {
        UUID deAyer = registroKm(ana, hoy.minusDays(1), "PENDIENTE", null);

        assertThat(registrar(deAyer, "6", 8000, sesionDe(ana)).statusCode()).isEqualTo(409);
        assertThat(estadoDe(deAyer)).isEqualTo("PENDIENTE");

        HttpResponse<byte[]> viejaLectura = enviar("POST", "/api/v1/habit-tracks/" + kmDeAnaHoy + "/auto-measurement",
                LECTURA.formatted("6", 8000, reloj.now().minusSeconds(2 * 86_400L)), sesionDe(ana));
        assertThat(viejaLectura.statusCode()).isEqualTo(400);
        assertThat(texto(viejaLectura)).contains("otro día");
    }

    @Test
    @DisplayName("la agenda de hoy trae la meta antes de cumplir, y después el origen y la fuente del día")
    void laAgendaTraeMetaYOrigen() throws Exception {
        JsonNode antes = trackDeHoy(ana, kmDeAnaHoy);
        assertThat(antes.at("/medicion/meta").decimalValue()).isEqualByComparingTo("5");
        assertThat(antes.at("/medicion/origenDelDia").isNull()).isTrue();

        assertThat(registrar(kmDeAnaHoy, "5.4", 7120, sesionDe(ana)).statusCode()).isEqualTo(200);

        JsonNode despues = trackDeHoy(ana, kmDeAnaHoy);
        assertThat(despues.at("/medicion/origenDelDia").asText()).isEqualTo("HEALTH_CONNECT");
        assertThat(despues.at("/medicion/fuenteDelDia").asText()).isEqualTo("Galaxy Watch");
        assertThat(despues.at("/medicion/valorDelDia").decimalValue()).isEqualByComparingTo("5.40");
    }

    // spec 037 FR-014
    @Test
    @DisplayName("el ranking de km no cambia sus cifras y marca «auto» solo a quien tiene días leídos por el teléfono")
    void elRankingMarcaAuto() throws Exception {
        UUID kmDeBetoHoy = registroKm(beto, hoy, "PENDIENTE", null);
        conCapturaSubida(beto, kmDeBetoHoy);
        assertThat(registrar(kmDeAnaHoy, "5.4", 7120, sesionDe(ana)).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/habit-tracks/" + kmDeBetoHoy + "/complete", "{\"valorMedido\": 3}",
                sesionDe(beto)).statusCode()).isEqualTo(200);

        generarRanking.generar(TipoRanking.KILOMETROS, hoy);

        JsonNode agregado = json.readTree(enviar("GET", "/api/v1/ranking?fecha=" + hoy, null, sesionDe(beto)).body());
        List<String> automaticos = new ArrayList<>();
        agregado.get("kilometrosAutomaticos").forEach(id -> automaticos.add(id.asText()));
        assertThat(automaticos).contains(ana.toString()).doesNotContain(beto.toString());
        assertThat(puntajeDe(agregado.get("kilometros"), ana)).isEqualByComparingTo("5.40");
        assertThat(puntajeDe(agregado.get("kilometros"), beto)).isEqualByComparingTo("3.00");
    }

    // spec 037 FR-011
    @Test
    @DisplayName("la base no deja una lectura automática a medias: sin fuente, dispositivo y hora no se guarda")
    void laBaseNoDejaLecturasIncompletas() {
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE renaser.registros_habito
                SET estado = CAST('COMPLETADO' AS renaser.estado_registro), valor_medido = 5, origen_medicion = 'HEALTH_CONNECT',
                    completado_en = now()
                WHERE id = ?""", kmDeAnaHoy)).isInstanceOf(DataAccessException.class);

        assertThat(jdbc.update("""
                UPDATE renaser.registros_habito
                SET estado = CAST('COMPLETADO' AS renaser.estado_registro), valor_medido = 5, origen_medicion = 'HEALTH_CONNECT',
                    fuente_medicion = 'Tu teléfono', dispositivo_medicion = 'TELEFONO', medicion_leida_en = now(),
                    completado_en = now()
                WHERE id = ?""", kmDeAnaHoy)).isEqualTo(1);
        // Y los pasos o la marca «por revisar» no caben en una medición escrita a mano.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE renaser.registros_habito SET origen_medicion = 'MANUAL', fuente_medicion = NULL, "
                        + "dispositivo_medicion = NULL, medicion_leida_en = NULL, pasos_medidos = 100 WHERE id = ?", kmDeAnaHoy))
                .isInstanceOf(DataAccessException.class);
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
        HttpResponse<byte[]> respuesta = enviar("GET", "/api/v1/habit-tracks/today", null, sesionDe(quien));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        for (JsonNode track : json.readTree(respuesta.body())) {
            if (track.get("id").asText().equals(registro.toString())) {
                return track;
            }
        }
        throw new AssertionError("el track no está en la agenda de hoy: " + registro);
    }

    private String estadoDe(UUID registro) {
        return jdbc.queryForObject("SELECT estado::text FROM renaser.registros_habito WHERE id = ?", String.class, registro);
    }

    private static String texto(HttpResponse<byte[]> respuesta) {
        return new String(respuesta.body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> registrar(UUID registro, String km, int pasos, String sesion) throws Exception {
        return enviar("POST", "/api/v1/habit-tracks/" + registro + "/auto-measurement",
                LECTURA.formatted(km, pasos, reloj.now()), sesion);
    }

    private HttpResponse<byte[]> enviar(String metodo, String ruta, String cuerpo, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (sesion != null) {
            pedido.header("X-Auth-Token", sesion);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofByteArray());
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

    /** Persona activa en su Día 12 (fecha de inicio coherente con el día: hoy − 11, regla 03). */
    private UUID persona(String nombre, String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
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

    /** La captura ya subida y confirmada, como la deja la app antes de completar con el número escrito (E-608). */
    private void conCapturaSubida(UUID quien, UUID registro) {
        jdbc.update("""
                INSERT INTO renaser.evidencias (participante_id, registro_habito_id, tipo, bucket, ruta_storage)
                VALUES (?, ?, 'FOTO', 'pruebas', ?)
                """, quien, registro, "evidencia-habitos/" + quien + "/" + registro + "/captura.jpg");
    }

    private UUID registroKm(UUID quien, LocalDate fecha, String estado, BigDecimal km) {
        UUID id = UUID.randomUUID();
        int dia = 12 - (int) (hoy.toEpochDay() - fecha.toEpochDay());
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa,
                                                      tipo_dia, es_opcional, estado, valor_medido, origen_medicion,
                                                      completado_en)
                VALUES (?, ?, ?, ?, ?, 'TODOS', true, CAST(? AS renaser.estado_registro), ?, ?,
                        CASE WHEN ? = 'COMPLETADO' THEN now() END)
                """, id, quien, HABITO_KM, fecha, dia, estado, km, km == null ? null : "MANUAL", estado);
        return id;
    }
}

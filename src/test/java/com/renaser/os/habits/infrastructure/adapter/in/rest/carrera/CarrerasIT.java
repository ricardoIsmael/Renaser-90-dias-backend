package com.renaser.os.habits.infrastructure.adapter.in.rest.carrera;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.MedicionDeLaObligacion;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.habits.application.ports.in.carrera.BorrarRutasViejasUseCase;
import com.renaser.os.habits.domain.model.carrera.PolylineDeGoogle;
import com.renaser.os.habits.domain.model.carrera.ZonaDePrivacidadTest;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.points.api.MedicionAcumuladaFinder;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las carreras con GPS (spec 042, D-323) de punta a punta sobre el Tomcat real, con sesión, Spring Security y Postgres con V170:
 * el envío único e idempotente (también a la vez), la regla «el mayor» con Health Connect, el día cerrado, la agenda, la ficha, la
 * ruta con su constancia y su borrado a los 180 días, y el interruptor de Soporte TI.
 *
 * <p>El reloj se fija a las 12:00 de Lima del 5 de noviembre (dentro del horario del hábito, 07:00–23:59): la carrera empezó a las
 * 11:00. Siembra sus propias personas y limpia lo que siembra (regla 03).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CarrerasIT {

    private static final UUID HABITO_KM = UUID.fromString("ea87fdec-d4c1-4c4f-9e61-1557bc7255d1");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant AHORA = Instant.parse("2026-11-05T17:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);
    /** Tres puntos del ejemplo de Google (no cuadra con 5 km: la carrera queda «por revisar», y no importa acá). */
    private static final String RUTA = "{\"polyline\": \"_p~iF~ps|U_ulLnnqC_mqNvxq`@\", \"puntos\": 3}";

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private CopiaDeEstados copiaDeEstados;
    @Autowired
    private ObligacionesHistoricasFinder obligaciones;
    @Autowired
    private MedicionAcumuladaFinder acumulados;
    @Autowired
    private BorrarRutasViejasUseCase borrarRutas;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private UUID cohorte;
    private UUID grupo;
    private UUID ana;
    private UUID beto;
    private UUID mentora;
    private UUID lider;
    private UUID admin;
    private UUID kmDeAna;

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        String marca = UUID.randomUUID().toString().substring(0, 8);
        ana = usuario("APRENDIZ", "Ana Corre " + marca);
        beto = usuario("APRENDIZ", "Beto Mira " + marca);
        mentora = usuario("MENTOR", "Luisa Mentora " + marca);
        lider = usuario("LIDER_MENTORES", "Líder " + marca);
        admin = usuario("ADMIN", "Admin " + marca);
        programa(ana);
        programa(beto);
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, ?, ?)", cohorte, "Cohorte " + marca,
                HOY.minusDays(60));
        grupo = grupo("Fénix " + marca, mentora);
        asignar(grupo, mentora, "MENTOR");
        asignar(grupo, ana, "APRENDIZ");
        kmDeAna = registroKm(ana, HOY, "PENDIENTE", null, null);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'habitos.km.carreras'");
        copiaDeEstados.recargar();
        jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", grupo);
        jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        usuarios.forEach(id -> {
            jdbc.update("DELETE FROM renaser.carreras WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            try {
                jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
            } catch (DataAccessException quedaReferenciado) {
                // Un oyente asíncrono (puntos, avisos) pudo dejar una fila que la referencia: queda como una persona más.
            }
        });
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    // spec 042 FR-016, FR-019, FR-020, FR-023, FR-027
    @Test
    @DisplayName("la primera vez guarda la carrera y cierra el hábito en el mismo pedido: 201, CERRADO, origen GPS y puntos")
    void laPrimeraVezCierraElHabito() throws Exception {
        UUID clave = UUID.randomUUID();

        HttpResponse<byte[]> respuesta = carrera(clave, "5000", 1800, null, sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(201);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("repetida").asBoolean()).isFalse();
        assertThat(cuerpo.get("fecha").asText()).isEqualTo(HOY.toString());
        assertThat(cuerpo.get("kilometros").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(cuerpo.get("cuentaParaElHabito").asBoolean()).isTrue();
        assertThat(cuerpo.at("/habito/resultado").asText()).isEqualTo("CERRADO");
        assertThat(cuerpo.at("/habito/registroId").asText()).isEqualTo(kmDeAna.toString());
        assertThat(cuerpo.at("/habito/origenDelDia").asText()).isEqualTo("GPS");
        assertThat(cuerpo.at("/habito/kilometrosDelDia").decimalValue()).isEqualByComparingTo("5.00");
        int puntos = cuerpo.at("/habito/puntosOtorgados").asInt();
        assertThat(puntos).isPositive();
        Map<String, Object> registro = jdbc.queryForMap("""
                SELECT estado::text AS estado, valor_medido, origen_medicion, puntos_otorgados, fuente_medicion
                  FROM renaser.registros_habito WHERE id = ?""", kmDeAna);
        assertThat(registro).containsEntry("estado", "COMPLETADO").containsEntry("origen_medicion", "GPS");
        assertThat(((Number) registro.get("puntos_otorgados")).intValue()).isEqualTo(puntos);
        assertThat(registro.get("fuente_medicion")).isNull();
        assertThat((BigDecimal) registro.get("valor_medido")).isEqualByComparingTo("5.00");
    }

    // spec 042 FR-019, SC-002
    @Test
    @DisplayName("cinco envíos iguales a la vez dejan una sola carrera, el mismo valor y los puntos pagados una vez")
    void cincoALaVez() throws Exception {
        UUID clave = UUID.randomUUID();
        String sesion = sesionDe(ana);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(5);
        List<Future<HttpResponse<byte[]>>> envios = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Callable<HttpResponse<byte[]>> envio = () -> {
                largada.await();
                return carrera(clave, "5000", 1800, null, sesion);
            };
            envios.add(hilos.submit(envio));
        }
        largada.countDown();
        List<Integer> codigos = new ArrayList<>();
        List<JsonNode> cuerpos = new ArrayList<>();
        for (Future<HttpResponse<byte[]>> envio : envios) {
            HttpResponse<byte[]> respuesta = envio.get();
            codigos.add(respuesta.statusCode());
            cuerpos.add(json.readTree(respuesta.body()));
        }
        hilos.shutdown();

        assertThat(codigos).containsOnly(201, 200).filteredOn(c -> c == 201).hasSize(1);
        assertThat(cuerpos).allSatisfy(c -> assertThat(c.at("/habito").toString()).isEqualTo(cuerpos.getFirst().at("/habito").toString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.carreras WHERE participante_id = ?", Integer.class, ana))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT valor_medido FROM renaser.registros_habito WHERE id = ?", BigDecimal.class, kmDeAna))
                .isEqualByComparingTo("5.00");
        assertThat(cuerpos.stream().filter(c -> !c.get("repetida").asBoolean()).count()).isEqualTo(1);
    }

    // spec 042 FR-019
    @Test
    @DisplayName("reenviar el mismo cuerpo responde 200 repetida con el mismo resultado; otro cuerpo con la misma clave, 409")
    void repetidaYOtraHuella() throws Exception {
        UUID clave = UUID.randomUUID();
        String sesion = sesionDe(ana);
        JsonNode primera = json.readTree(carrera(clave, "5000", 1800, null, sesion).body());

        HttpResponse<byte[]> repetida = carrera(clave, "5000", 1800, null, sesion);
        HttpResponse<byte[]> otra = carrera(clave, "6000", 1800, null, sesion);

        assertThat(repetida.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(repetida.body());
        assertThat(cuerpo.get("repetida").asBoolean()).isTrue();
        assertThat(cuerpo.get("habito")).isEqualTo(primera.get("habito"));
        assertThat(otra.statusCode()).isEqualTo(409);
        assertThat(json.readTree(otra.body()).get("codigo").asText()).isEqualTo("CARRERA_YA_REGISTRADA");
        assertThat(jdbc.queryForObject("SELECT distancia_m FROM renaser.carreras WHERE participante_id = ? AND carrera_id = ?",
                BigDecimal.class, ana, clave)).isEqualByComparingTo("5000");
    }

    // spec 042 FR-022
    @Test
    @DisplayName("dos carreras de 3 y 4 km el mismo día dejan el hábito en 7 km y los puntos pagados una sola vez")
    void dosCarrerasSeSuman() throws Exception {
        String sesion = sesionDe(ana);
        JsonNode primera = json.readTree(carrera(UUID.randomUUID(), "3000", 1200, null, sesion).body());
        JsonNode segunda = json.readTree(carrera(UUID.randomUUID(), "4000", 1600, null, sesion).body());

        assertThat(primera.at("/habito/resultado").asText()).isEqualTo("CERRADO");
        assertThat(segunda.at("/habito/resultado").asText()).isEqualTo("ACTUALIZADO");
        assertThat(segunda.at("/habito/puntosOtorgados").asInt()).isZero();
        assertThat(segunda.at("/habito/kilometrosDelDia").decimalValue()).isEqualByComparingTo("7.00");
        assertThat(jdbc.queryForObject("SELECT puntos_otorgados FROM renaser.registros_habito WHERE id = ?", Integer.class, kmDeAna))
                .isEqualTo(primera.at("/habito/puntosOtorgados").asInt());
    }

    // spec 042 FR-022, SC-008
    @Test
    @DisplayName("7 km de Health Connect y una carrera de 3 quedan en 7 (el mayor); después Health Connect con 9 sube a 9")
    void elMayorConHealthConnect() throws Exception {
        String sesion = sesionDe(ana);
        assertThat(lecturaDeHealthConnect("7", sesion).statusCode()).isEqualTo(200);

        JsonNode carrera = json.readTree(carrera(UUID.randomUUID(), "3000", 1200, null, sesion).body());

        assertThat(carrera.at("/habito/resultado").asText()).isEqualTo("SIN_CAMBIO");
        assertThat(carrera.at("/habito/kilometrosDelDia").decimalValue()).isEqualByComparingTo("7.00");
        assertThat(carrera.at("/habito/origenDelDia").asText()).isEqualTo("HEALTH_CONNECT");
        // Una carrera mayor pasa el día a GPS, y Health Connect con más vuelve a subirlo, sin puntos de más.
        int puntos = jdbc.queryForObject("SELECT puntos_otorgados FROM renaser.registros_habito WHERE id = ?", Integer.class, kmDeAna);
        assertThat(json.readTree(carrera(UUID.randomUUID(), "5000", 1800, null, sesion).body()).at("/habito/kilometrosDelDia")
                .decimalValue()).isEqualByComparingTo("8.00");
        assertThat(lecturaDeHealthConnect("9", sesion).statusCode()).isEqualTo(200);
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT valor_medido, origen_medicion, puntos_otorgados FROM renaser.registros_habito WHERE id = ?", kmDeAna);
        assertThat((BigDecimal) fila.get("valor_medido")).isEqualByComparingTo("9.00");
        assertThat(fila).containsEntry("origen_medicion", "HEALTH_CONNECT");
        assertThat(((Number) fila.get("puntos_otorgados")).intValue()).isEqualTo(puntos);
    }

    // spec 042 FR-023
    @Test
    @DisplayName("con la captura ya registrada, la carrera se guarda y no toca el hábito")
    void conLaCaptura() throws Exception {
        jdbc.update("DELETE FROM renaser.registros_habito WHERE id = ?", kmDeAna);
        UUID conCaptura = registroKm(ana, HOY, "COMPLETADO", new BigDecimal("4.00"), "MANUAL");

        JsonNode cuerpo = json.readTree(carrera(UUID.randomUUID(), "5000", 1800, null, sesionDe(ana)).body());

        assertThat(cuerpo.at("/habito/resultado").asText()).isEqualTo("YA_CON_CAPTURA");
        assertThat(jdbc.queryForObject("SELECT origen_medicion FROM renaser.registros_habito WHERE id = ?", String.class, conCaptura))
                .isEqualTo("MANUAL");
    }

    // spec 042 FR-021, FR-023, SC-009
    @Test
    @DisplayName("una carrera de un día que ya cerró se guarda en su día y no cambia ningún registro (llegó sin señal hasta hoy)")
    void diaCerrado() throws Exception {
        UUID deAyer = registroKm(ana, HOY.minusDays(1), "PENDIENTE", null, null);
        Instant ayerALas7 = HOY.minusDays(1).atTime(7, 0).atZone(LIMA).toInstant();

        HttpResponse<byte[]> respuesta = enviar("POST", "/api/v1/carreras", cuerpo(UUID.randomUUID(), ayerALas7, "5000", 1800, null),
                sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(201);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("fecha").asText()).isEqualTo(HOY.minusDays(1).toString());
        assertThat(cuerpo.at("/habito/resultado").asText()).isEqualTo("DIA_CERRADO");
        assertThat(estadoDe(deAyer)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(kmDeAna)).isEqualTo("PENDIENTE");
    }

    // spec 042 FR-024
    @Test
    @DisplayName("una carrera a más de 25 km/h se guarda y no cuenta: el hábito sigue pendiente")
    void noCuenta() throws Exception {
        JsonNode cuerpo = json.readTree(carrera(UUID.randomUUID(), "20000", 1800, null, sesionDe(ana)).body());

        assertThat(cuerpo.get("cuentaParaElHabito").asBoolean()).isFalse();
        assertThat(cuerpo.get("motivoNoCuenta").asText()).contains("25 km/h");
        assertThat(cuerpo.at("/habito/resultado").asText()).isEqualTo("NO_CUENTA");
        assertThat(estadoDe(kmDeAna)).isEqualTo("PENDIENTE");
    }

    // spec 042 FR-026
    @Test
    @DisplayName("fuera de los topes es un 400 que la persona lee, y no se guarda nada")
    void fueraDeTopes() throws Exception {
        String sesion = sesionDe(ana);

        HttpResponse<byte[]> corta = carrera(UUID.randomUUID(), "80", 1800, null, sesion);
        HttpResponse<byte[]> sinClave = enviar("POST", "/api/v1/carreras", "{\"distanciaMetros\": 5000}", sesion);

        assertThat(corta.statusCode()).isEqualTo(400);
        assertThat(new String(corta.body(), StandardCharsets.UTF_8)).contains("menos de 100 m");
        assertThat(sinClave.statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.carreras WHERE participante_id = ?", Integer.class, ana))
                .isZero();
    }

    @Test
    @DisplayName("una cuenta suspendida recibe 403 y no guarda nada")
    void suspendida() throws Exception {
        String sesion = sesionDe(ana);
        jdbc.update("UPDATE renaser.usuarios SET estado = CAST('SUSPENDIDO' AS renaser.estado_usuario) WHERE id = ?", ana);

        assertThat(carrera(UUID.randomUUID(), "5000", 1800, null, sesion).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.carreras WHERE participante_id = ?", Integer.class, ana))
                .isZero();
    }

    // spec 042 FR-045
    @Test
    @DisplayName("con habitos.km.carreras apagada, el envío es un 503 y no guarda nada (la app la deja en su cola)")
    void interruptorApagado() throws Exception {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'habitos.km.carreras'");
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('habitos.km.carreras', false, nextval('renaser.funcionalidades_version_seq'), ?, now())", admin);
        copiaDeEstados.recargar();

        assertThat(carrera(UUID.randomUUID(), "5000", 1800, null, sesionDe(ana)).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.carreras WHERE participante_id = ?", Integer.class, ana))
                .isZero();
    }

    // spec 042 FR-029
    @Test
    @DisplayName("la agenda de hoy trae carrerasDelDia siempre en el hábito de km (0 antes) y el origen GPS después")
    void laAgenda() throws Exception {
        JsonNode antes = trackDeHoy(ana);
        assertThat(antes.at("/medicion/carrerasDelDia/cantidad").asInt()).isZero();
        assertThat(antes.at("/medicion/carrerasDelDia/kilometros").decimalValue()).isEqualByComparingTo("0");

        carrera(UUID.randomUUID(), "3000", 1200, null, sesionDe(ana));
        carrera(UUID.randomUUID(), "20000", 1800, null, sesionDe(ana));

        JsonNode despues = trackDeHoy(ana);
        assertThat(despues.at("/medicion/origenDelDia").asText()).isEqualTo("GPS");
        assertThat(despues.at("/medicion/carrerasDelDia/cantidad").asInt()).isEqualTo(2);
        assertThat(despues.at("/medicion/carrerasDelDia/kilometros").decimalValue()).isEqualByComparingTo("3.00");
    }

    // spec 042 FR-044
    @Test
    @DisplayName("en la ficha, el día con carreras trae la lista resumida: km, duración, ritmo, por revisar y si tiene ruta")
    void laFicha() throws Exception {
        UUID clave = UUID.randomUUID();
        carrera(clave, "5000", 1800, RUTA, sesionDe(ana));

        ObligacionHabito km = obligaciones.porParticipantesEntre(List.of(UserId.of(ana)), HOY, HOY).stream()
                .filter(o -> HABITO_KM.equals(registroDe(o))).findFirst().orElseThrow();

        MedicionDeLaObligacion medicion = km.medicion();
        assertThat(medicion.origen()).isEqualTo("GPS");
        assertThat(medicion.carreras()).singleElement().satisfies(c -> {
            assertThat(c.carreraId()).isEqualTo(clave);
            assertThat(c.kilometros()).isEqualByComparingTo("5.00");
            assertThat(c.duracionActivaSeg()).isEqualTo(1800);
            assertThat(c.ritmoSegPorKm()).isEqualTo(360);
            assertThat(c.porRevisar()).isTrue();
            assertThat(c.tieneRuta()).isTrue();
        });
    }

    // spec 042 FR-028
    @Test
    @DisplayName("el ranking de km marca «auto» a quien tiene días con carreras con GPS")
    void elRankingLaMarcaAuto() throws Exception {
        carrera(UUID.randomUUID(), "5000", 1800, null, sesionDe(ana));

        assertThat(acumulados.conKilometrosAutomaticos(List.of(UserId.of(ana), UserId.of(beto)), HOY))
                .containsExactly(UserId.of(ana));
    }

    // spec 042 FR-040, FR-041, SC-010
    @Test
    @DisplayName("la ruta: la persona, su mentora, el líder y Administración la ven; cada lectura ajena deja constancia; nadie más")
    void laRutaYQuienLaVe() throws Exception {
        UUID conRuta = UUID.randomUUID();
        UUID sinRuta = UUID.randomUUID();
        carrera(conRuta, "5000", 1800, RUTA, sesionDe(ana));
        carrera(sinRuta, "3000", 1200, null, sesionDe(ana));
        String propia = "/api/v1/carreras/" + conRuta + "/ruta";

        assertThat(enviar("GET", propia, null, sesionDe(ana)).statusCode()).isEqualTo(200);
        assertThat(json.readTree(enviar("GET", propia, null, sesionDe(ana)).body()).get("puntos").asInt()).isEqualTo(3);
        assertThat(enviar("GET", "/api/v1/carreras/" + sinRuta + "/ruta", null, sesionDe(ana)).statusCode()).isEqualTo(404);
        assertThat(constanciasDe(ana)).isZero();

        assertThat(enviar("GET", "/api/v1/mentor/groups/" + grupo + "/learners/" + ana + "/carreras/" + conRuta + "/ruta", null,
                sesionDe(mentora)).statusCode()).isEqualTo(200);
        assertThat(enviar("GET", "/api/v1/leadership/groups/" + grupo + "/people/" + ana + "/carreras/" + conRuta + "/ruta", null,
                sesionDe(lider)).statusCode()).isEqualTo(200);
        assertThat(enviar("GET", "/api/v1/leadership/people/" + ana + "/carreras/" + conRuta + "/ruta", null, sesionDe(admin))
                .statusCode()).isEqualTo(200);
        esperarConstancias(ana, 3);

        // Otra aprendiz: con su propia puerta no la encuentra (404) y por la de liderazgo o de mentor, 403.
        assertThat(enviar("GET", propia, null, sesionDe(beto)).statusCode()).isEqualTo(404);
        assertThat(enviar("GET", "/api/v1/leadership/people/" + ana + "/carreras/" + conRuta + "/ruta", null, sesionDe(beto))
                .statusCode()).isEqualTo(403);
        assertThat(enviar("GET", "/api/v1/mentor/groups/" + grupo + "/learners/" + ana + "/carreras/" + conRuta + "/ruta", null,
                sesionDe(beto)).statusCode()).isEqualTo(403);
        // Un líder suspendido tampoco.
        String sesionDelLider = sesionDe(lider);
        jdbc.update("UPDATE renaser.usuarios SET estado = CAST('SUSPENDIDO' AS renaser.estado_usuario) WHERE id = ?", lider);
        assertThat(enviar("GET", "/api/v1/leadership/people/" + ana + "/carreras/" + conRuta + "/ruta", null, sesionDelLider)
                .statusCode()).isEqualTo(403);
    }

    // spec 042 FR-043, FR-019
    @Test
    @DisplayName("el teléfono manda la ruta completa: se guarda y se lee sin sus primeros ni sus últimos 200 m, y repetirla es 200")
    void laZonaDePrivacidadLaQuitaElServidor() throws Exception {
        UUID clave = UUID.randomUUID();
        String completa = ZonaDePrivacidadTest.rutaCompleta();
        String ruta = "{\"polyline\": \"" + completa.replace("\\", "\\\\") + "\", \"puntos\": 180}";

        HttpResponse<byte[]> primera = carrera(clave, "1830", 660, ruta, sesionDe(ana));
        HttpResponse<byte[]> otraVez = carrera(clave, "1830", 660, ruta, sesionDe(ana));

        assertThat(primera.statusCode()).isEqualTo(201);
        assertThat(json.readTree(primera.body()).get("porRevisar").asBoolean()).isFalse();
        assertThat(otraVez.statusCode()).isEqualTo(200);
        assertThat(json.readTree(otraVez.body()).get("repetida").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT ruta_polyline FROM renaser.carreras WHERE carrera_id = ?", String.class, clave))
                .isEqualTo(ZonaDePrivacidadTest.recortadaPorLaApp());
        JsonNode deLaMentora = json.readTree(enviar("GET", "/api/v1/mentor/groups/" + grupo + "/learners/" + ana + "/carreras/"
                + clave + "/ruta", null, sesionDe(mentora)).body());
        JsonNode delLider = json.readTree(enviar("GET", "/api/v1/leadership/people/" + ana + "/carreras/" + clave + "/ruta", null,
                sesionDe(lider)).body());
        assertThat(deLaMentora.get("polyline").asText()).isEqualTo(ZonaDePrivacidadTest.recortadaPorLaApp());
        assertThat(delLider.get("polyline").asText()).isEqualTo(ZonaDePrivacidadTest.recortadaPorLaApp());
    }

    // spec 042 FR-043
    @Test
    @DisplayName("una ruta de menos de 400 m: la carrera se guarda y cuenta, sin ruta")
    void rutaCortaSinRuta() throws Exception {
        UUID clave = UUID.randomUUID();
        List<double[]> puntos = PolylineDeGoogle.decodificar(ZonaDePrivacidadTest.rutaCompleta()).subList(0, 30);
        String corta = PolylineDeGoogle.codificar(puntos).replace("\\", "\\\\");

        HttpResponse<byte[]> respuesta = carrera(clave, "300", 180, "{\"polyline\": \"" + corta + "\", \"puntos\": 30}",
                sesionDe(ana));

        assertThat(respuesta.statusCode()).isEqualTo(201);
        assertThat(json.readTree(respuesta.body()).get("cuentaParaElHabito").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT ruta_polyline IS NULL FROM renaser.carreras WHERE carrera_id = ?", Boolean.class,
                clave)).isTrue();
        assertThat(enviar("GET", "/api/v1/carreras/" + clave + "/ruta", null, sesionDe(ana)).statusCode()).isEqualTo(404);
    }

    // spec 042 FR-042
    @Test
    @DisplayName("a los 180 días la ruta se borra y queda el resumen de la carrera")
    void laRutaSeBorraALos180Dias() throws Exception {
        UUID vieja = UUID.randomUUID();
        UUID nueva = UUID.randomUUID();
        carrera(vieja, "5000", 1800, RUTA, sesionDe(ana));
        carrera(nueva, "3000", 1200, RUTA, sesionDe(ana));
        jdbc.update("UPDATE renaser.carreras SET iniciada_en = iniciada_en - interval '181 days', terminada_en = terminada_en - "
                + "interval '181 days' WHERE participante_id = ? AND carrera_id = ?", ana, vieja);

        int borradas = borrarRutas.borrarRutasViejas();

        assertThat(borradas).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT ruta_polyline, ruta_puntos, distancia_m FROM renaser.carreras WHERE carrera_id = ?", vieja))
                .containsEntry("ruta_polyline", null).containsEntry("ruta_puntos", null);
        assertThat(jdbc.queryForObject("SELECT ruta_polyline IS NOT NULL FROM renaser.carreras WHERE carrera_id = ?", Boolean.class,
                nueva)).isTrue();
        assertThat(enviar("GET", "/api/v1/carreras/" + vieja + "/ruta", null, sesionDe(ana)).statusCode()).isEqualTo(404);
    }

    // spec 042 FR-019
    @Test
    @DisplayName("la base no deja dos carreras con la misma clave para la misma persona, ni una fila que mienta")
    void laBaseLoImpide() throws Exception {
        UUID clave = UUID.randomUUID();
        carrera(clave, "5000", 1800, null, sesionDe(ana));

        assertThat(intentar("""
                INSERT INTO renaser.carreras (id, participante_id, carrera_id, huella, fecha, iniciada_en, terminada_en,
                       duracion_activa_seg, distancia_m, tramos, cuenta_para_el_habito, resultado_habito)
                SELECT gen_random_uuid(), participante_id, carrera_id, huella, fecha, iniciada_en, terminada_en,
                       duracion_activa_seg, distancia_m, tramos, true, 'SIN_CAMBIO'
                  FROM renaser.carreras WHERE carrera_id = ?""", clave)).isFalse();
        assertThat(intentar("UPDATE renaser.carreras SET puntos_otorgados = 5, resultado_habito = 'ACTUALIZADO' WHERE carrera_id = ?",
                clave)).isFalse();
        assertThat(intentar("UPDATE renaser.carreras SET cuenta_para_el_habito = false WHERE carrera_id = ?", clave)).isFalse();
    }

    // ── pedidos y semilla ──────────────────────────────────────────────────

    private boolean intentar(String sql, Object... args) {
        try {
            jdbc.update(sql, args);
            return true;
        } catch (DataAccessException rechazada) {
            return false;
        }
    }

    private UUID registroDe(ObligacionHabito o) {
        return jdbc.queryForObject("SELECT habito_id FROM renaser.registros_habito WHERE id = ?", UUID.class, o.registroId());
    }

    private int constanciasDe(UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id = ? AND tipo = 'RUTA_DE_CARRERA'",
                Integer.class, persona);
    }

    private void esperarConstancias(UUID persona, int cuantas) throws InterruptedException {
        long limite = System.currentTimeMillis() + 20_000;
        while (constanciasDe(persona) < cuantas && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
        }
        assertThat(constanciasDe(persona)).isEqualTo(cuantas);
        assertThat(jdbc.queryForList("SELECT rol_consultor FROM renaser.consultas_de_ficha WHERE persona_id = ? "
                + "AND tipo = 'RUTA_DE_CARRERA'", String.class, persona)).containsExactlyInAnyOrder("MENTOR", "MENTOR_LEAD", "ADMIN");
    }

    private JsonNode trackDeHoy(UUID quien) throws Exception {
        HttpResponse<byte[]> respuesta = enviar("GET", "/api/v1/habit-tracks/today", null, sesionDe(quien));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        for (JsonNode track : json.readTree(respuesta.body())) {
            if (track.get("id").asText().equals(kmDeAna.toString())) {
                return track;
            }
        }
        throw new AssertionError("el track de km no está en la agenda de hoy");
    }

    private String estadoDe(UUID registro) {
        return jdbc.queryForObject("SELECT estado::text FROM renaser.registros_habito WHERE id = ?", String.class, registro);
    }

    private HttpResponse<byte[]> lecturaDeHealthConnect(String km, String sesion) throws Exception {
        return enviar("POST", "/api/v1/habit-tracks/" + kmDeAna + "/auto-measurement", """
                {"kilometros": %s, "pasos": %d, "fuente": "Tu teléfono", "dispositivo": "TELEFONO", "leidoEn": "%s"}"""
                .formatted(km, Integer.parseInt(km) * 1300, AHORA.minusSeconds(60)), sesion);
    }

    private HttpResponse<byte[]> carrera(UUID clave, String metros, int segundos, String ruta, String sesion) throws Exception {
        return enviar("POST", "/api/v1/carreras", cuerpo(clave, AHORA.minus(Duration.ofHours(1)), metros, segundos, ruta), sesion);
    }

    /** Tramos parejos de km completos; el día y la hora salen del inicio. */
    private static String cuerpo(UUID clave, Instant inicio, String metros, int segundos, String ruta) {
        int completos = (int) (Double.parseDouble(metros) / 1000);
        int porKm = completos == 0 ? segundos : (int) Math.round(segundos * 1000.0 / Double.parseDouble(metros));
        StringBuilder tramos = new StringBuilder();
        for (int km = 1; km <= completos; km++) {
            tramos.append(km == 1 ? "" : ",").append("{\"km\":").append(km).append(",\"segundos\":").append(porKm).append('}');
        }
        return """
                {"carreraId": "%s", "iniciadaEn": "%s", "terminadaEn": "%s", "duracionActivaSeg": %d, "distanciaMetros": %s,
                 "subidaMetros": 12, "caloriasAprox": 300, "tramos": [%s]%s, "appVersion": "1.8.0"}"""
                .formatted(clave, inicio, inicio.plusSeconds(segundos + 60L), segundos, metros, tramos,
                        ruta == null ? "" : ", \"ruta\": " + ruta);
    }

    private HttpResponse<byte[]> enviar(String metodo, String ruta, String cuerpo, String sesion) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        pedido.header("X-Auth-Token", sesion);
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

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        usuarios.add(id);
        return id;
    }

    /** En su Día 21: fecha de inicio coherente con el día (hoy − 20, regla 03). */
    private void programa(UUID id) {
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone)
                VALUES (?, 21, ?, ?, 'America/Lima')
                """, id, HOY.minusDays(20), java.sql.Timestamp.from(AHORA.minus(Duration.ofDays(21))));
    }

    private UUID grupo(String nombre, UUID mentor) {
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?) ON CONFLICT DO NOTHING", mentor);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), ?, ?, ?)
                """, id, nombre, cohorte, HOY.minusDays(5), HOY.plusDays(20), mentor);
        return id;
    }

    private void asignar(UUID grupo, UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), ?, NULL, 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, java.sql.Timestamp.from(AHORA.minus(Duration.ofHours(2))), "prueba|" + id);
    }

    private UUID registroKm(UUID quien, LocalDate fecha, String estado, BigDecimal km, String origen) {
        UUID id = UUID.randomUUID();
        int dia = 21 - (int) (HOY.toEpochDay() - fecha.toEpochDay());
        jdbc.update("""
                INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa, tipo_dia,
                                                      es_opcional, estado, valor_medido, origen_medicion, completado_en)
                VALUES (?, ?, ?, ?, ?, 'TODOS', true, CAST(? AS renaser.estado_registro), ?, ?, CASE WHEN ? = 'COMPLETADO' THEN now() END)
                """, id, quien, HABITO_KM, fecha, dia, estado, km, origen, estado);
        return id;
    }
}

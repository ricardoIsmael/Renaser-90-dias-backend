package com.renaser.os.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
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
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Acuerdo de transformación (spec 039, D-312) de punta a punta con Postgres, sesión real y el {@link RelojDePrueba} compartido (regla 03): la migración V162 con el
 * texto del dueño en las tres formaciones (y su idempotencia), «Mi aceptación» con las fechas de la spec 036, lo que la constancia guarda de lo que se mostró, y la firma
 * del cronograma de pagos con la misma confirmación de la spec 032.
 *
 * <p>La base de las pruebas de edición de la spec 027 es la que dejó la V149; aquí se parte de ella y se corre la V162 encima, tal cual la corre Flyway. Las cuentas que
 * se siembran se borran al terminar (con ellas, sus cuotas y sus aceptaciones) y los términos vuelven a esa base. El reloj se fija en la medianoche de Lima: las 04:59 UTC del
 * 3 de noviembre son las 23:59 del 2 en Lima, así que «mañana» es el 3 aunque el calendario de UTC ya diga 3 (regla 02).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AcuerdoDeTransformacionIT {

    private static final Instant ONCE_Y_MEDIA_EN_LIMA = Instant.parse("2026-11-03T04:59:00Z"); // 23:59 del 2 en Lima: mañana es el 3
    private static final Instant PASADA_LA_MEDIANOCHE_EN_LIMA = Instant.parse("2026-11-03T05:01:00Z"); // 00:01 del 3 en Lima: mañana es el 4
    private static final String TERMINOS = "/api/v1/onboarding/terms";
    private static final String HITOS = "/api/v1/onboarding/milestones";
    private static final String PAGOS = "/api/v1/admin/pagos/personas/";
    private static final String SCRIPT = "db/migration/V162__acuerdo_de_transformacion.sql";

    /** Cómo empieza cada una de las 23 cláusulas del texto del dueño (el primer párrafo). */
    private static final List<String> INICIOS_DE_LAS_CLAUSULAS = List.of(
            "Declaro que, previo a mi inscripci",
            "Reconozco que el contenido del pro",
            "Comprendo que la metodología RENAS",
            "Me comprometo a asistir puntualmen",
            "Reconozco que las inasistencias re",
            "Acepto que la duración, fecha de i",
            "Reconozco que NASER es el aplicati",
            "Entiendo y acepto que el acceso al",
            "Reconozco que cualquier uso compar",
            "Reconozco que la totalidad del con",
            "Este reconocimiento se extiende a ",
            "Declaro que el programa tiene como",
            "Asumo responsabilidad por la imple",
            "Reconozco que el programa no ha si",
            "Entiendo que durante el proceso pu",
            "Me comprometo a mantener una condu",
            "Asumo la responsabilidad de propor",
            "Reconozco que RENASER documenta lo",
            "Acepto que la inversión, modalidad",
            "Reconozco que los retiros, cancela",
            "Reconozco que los eventos presenci",
            "Entiendo que las herramientas de i",
            "Declaro haber leído y comprendido ");

    /** Las cláusulas de dos párrafos (número) y cómo empieza el segundo. */
    private static final Map<Integer, String> SEGUNDOS_PARRAFOS = Map.of(
            18, "Autorizo expresamente el uso d",
            21, "Dispongo de siete días hábiles",
            22, "Comprendo que el tratamiento d",
            23, "Acepto que cualquier controver");

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> cuentas = new ArrayList<>();

    @BeforeEach
    void partirDeLaV149YCorrerLaV162() throws Exception {
        reloj.fijar(ONCE_Y_MEDIA_EN_LIMA);
        TerminosDePrueba.restablecer(jdbc);
        correrLaV162();
    }

    @AfterEach
    void limpiar() {
        cuentas.reversed().forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        TerminosDePrueba.restablecer(jdbc);
    }

    // ── V162 ────────────────────────────────────────────────────────────────────────────────────────────────────────

    // spec 039 FR-002
    @Test
    @DisplayName("V162 publica el texto del dueño en las tres formaciones: la Escuela pasa a la versión 2 (pide aceptar de nuevo) y el Diplomado y la Certificación quedan con la 1")
    void v162PublicaElTextoEnLasTresFormaciones() {
        assertThat(jdbc.queryForList("SELECT formacion || ':' || numero FROM renaser.terminos_versiones WHERE estado = 'PUBLICADA' ORDER BY formacion, numero", String.class))
                .containsExactly("CERTIFICACION:1", "DIPLOMADO:1", "ESCUELA:1", "ESCUELA:2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.terminos_versiones WHERE estado = 'BORRADOR'", Integer.class))
                .as("el borrador copia de la spec 027 ya no sirve").isZero();
        assertThat(jdbc.queryForObject("SELECT pide_nueva_aceptacion FROM renaser.terminos_versiones WHERE formacion = 'ESCUELA' AND numero = 2", Boolean.class))
                .as("quien ya estaba dentro lo acepta de nuevo").isTrue();
        assertThat(jdbc.queryForObject("SELECT bool_or(pide_nueva_aceptacion) FROM renaser.terminos_versiones WHERE numero = 1 AND formacion <> 'ESCUELA'", Boolean.class))
                .as("la primera versión de una formación nunca pide aceptar de nuevo").isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.terminos_versiones WHERE id::text LIKE '00000000-0000-4000-8000-0000000162%' AND publicada_por IS NULL "
                + "AND publicada_en IS NOT NULL", Integer.class)).as("publicadas a nombre del sistema, con su fecha").isEqualTo(3);
    }

    // spec 039 FR-001, SC-001
    @Test
    @DisplayName("el texto de cada formación es el del dueño tal cual: 23 cláusulas numeradas, con el nombre del programa en la introducción y sin marcadores")
    void elTextoPorFormacion() {
        Map<String, String> programas = Map.of("ESCUELA", "Formación RENASER", "DIPLOMADO", "Diplomado RENASER", "CERTIFICACION", "Certificación RENASER");
        for (Map.Entry<String, String> programa : programas.entrySet()) {
            Map<String, Object> version = jdbc.queryForMap("SELECT titulo, introduccion, texto_casilla, clausulas FROM renaser.terminos_versiones "
                    + "WHERE formacion = ? AND estado = 'PUBLICADA' ORDER BY numero DESC LIMIT 1", programa.getKey());

            assertThat(version.get("titulo")).isEqualTo("Términos y condiciones");
            assertThat(version.get("introduccion")).isEqualTo("Al inscribirme en " + programa.getValue()
                    + ", declaro que lo hago de manera libre y voluntaria, aceptando íntegramente los siguientes términos:");
            assertThat(version.get("texto_casilla")).isEqualTo("He leído y acepto los Términos y Condiciones");
            List<String> clausulas = List.of((String[]) aArreglo(version.get("clausulas")));
            assertThat(clausulas).as(programa.getKey()).hasSize(23);
            for (int i = 0; i < 23; i++) {
                assertThat(clausulas.get(i)).as("%s cláusula %d", programa.getKey(), i + 1).startsWith(INICIOS_DE_LAS_CLAUSULAS.get(i));
                assertThat(clausulas.get(i)).doesNotContain("{", "}", "[", "]", "PROGRAMA");
            }
            SEGUNDOS_PARRAFOS.forEach((numero, inicio) -> {
                String[] parrafos = clausulas.get(numero - 1).split("\n\n");
                assertThat(parrafos).as("la cláusula %d conserva sus dos párrafos", numero).hasSize(2);
                assertThat(parrafos[1]).startsWith(inicio);
            });
            assertThat(clausulas.stream().filter(c -> c.contains("\n\n")).count()).as("solo las cláusulas 18, 21, 22 y 23 llevan dos párrafos").isEqualTo(4);
            assertThat(clausulas.get(18)).contains("Acepto que la inversión, modalidad de pago y fechas de vencimiento", "cronograma aceptado.");
            assertThat(clausulas.get(5)).contains("NASER");
        }
    }

    // spec 039 FR-002
    @Test
    @DisplayName("V162 es idempotente: correrla otra vez no duplica versiones ni columnas ni toca lo publicado")
    void v162EsIdempotente() throws Exception {
        List<String> antes = jdbc.queryForList("SELECT id || ':' || formacion || ':' || numero FROM renaser.terminos_versiones ORDER BY id", String.class);

        correrLaV162();
        correrLaV162();

        assertThat(jdbc.queryForList("SELECT id || ':' || formacion || ':' || numero FROM renaser.terminos_versiones ORDER BY id", String.class)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'renaser' AND table_name = 'aceptaciones_de_terminos' "
                + "AND column_name IN ('programa_mostrado', 'inicio_mostrado', 'fin_mostrado', 'cronograma_huella')", Integer.class)).isEqualTo(4);
    }

    // spec 039 FR-002
    @Test
    @DisplayName("un borrador que Administración ya editó se conserva; el que sigue idéntico a la copia de la spec 027 se descarta")
    void unBorradorEditadoSeConserva() throws Exception {
        TerminosDePrueba.restablecer(jdbc);
        jdbc.update("UPDATE renaser.terminos_versiones SET titulo = 'Mis términos del Diplomado' WHERE formacion = 'DIPLOMADO' AND estado = 'BORRADOR'");

        correrLaV162();

        assertThat(jdbc.queryForList("SELECT formacion || ':' || estado FROM renaser.terminos_versiones WHERE estado = 'BORRADOR'", String.class))
                .as("el de la Certificación seguía idéntico y se descartó; el editado del Diplomado queda").containsExactly("DIPLOMADO:BORRADOR");
        assertThat(jdbc.queryForObject("SELECT titulo FROM renaser.terminos_versiones WHERE formacion = 'DIPLOMADO' AND estado = 'BORRADOR'", String.class))
                .isEqualTo("Mis términos del Diplomado");
        assertThat(jdbc.queryForObject("SELECT numero FROM renaser.terminos_versiones WHERE formacion = 'DIPLOMADO' AND estado = 'PUBLICADA'", Integer.class)).isEqualTo(1);
    }

    // spec 039 FR-010
    @Test
    @DisplayName("el editor de Administración (spec 027) muestra las 23 cláusulas, con sus dos párrafos, y deja editarlas: queda un borrador sobre la versión nueva")
    void elEditorDeAdministracionLasMuestraYLasDejaEditar() throws Exception {
        String admin = sesionDe(usuario("ADMIN", "Ana Admin"));

        JsonNode detalle = json.readTree(ok(enviar("GET", "/api/v1/admin/terms/ESCUELA", admin, null)));
        assertThat(detalle.get("current").get("number").asInt()).isEqualTo(2);
        JsonNode clausulas = detalle.get("current").get("clauses");
        assertThat(clausulas).hasSize(23);
        assertThat(clausulas.get(17).asText()).contains("\n\n");

        List<String> editadas = new ArrayList<>();
        clausulas.forEach(c -> editadas.add(c.asText()));
        editadas.set(17, editadas.get(17) + " (revisada)");
        Map<String, Object> borrador = new HashMap<>();
        borrador.put("title", detalle.get("current").get("title").asText());
        borrador.put("introduction", detalle.get("current").get("introduction").asText());
        borrador.put("clauses", editadas);
        borrador.put("checkboxText", detalle.get("current").get("checkboxText").asText());
        borrador.put("baseNumber", 2);
        JsonNode guardado = json.readTree(ok(enviar("PUT", "/api/v1/admin/terms/ESCUELA/draft", admin, borrador)));
        assertThat(guardado.get("clauses")).hasSize(23);
        assertThat(guardado.get("clauses").get(17).asText()).contains("\n\n").endsWith("(revisada)");
    }

    // ── La persona: «Mi aceptación» ─────────────────────────────────────────────────────────────────────────────────

    // spec 039 FR-003, FR-004, FR-005, SC-003
    @Test
    @DisplayName("sin cronograma: «Mi aceptación» trae el nombre completo, el programa, mañana de Lima y el fin 89 días después; sin sección de cronograma")
    void sinCronograma() throws Exception {
        UUID ana = aprendiz("ESCUELA", 90, "Ana María Pérez Soto", false);
        String sesion = sesionDe(ana);
        asignarPago(Map.of("mode", "NONE"), ana);

        JsonNode mios = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));

        assertThat(mios.get("pending").asText()).isEqualTo("FIRST_TIME");
        assertThat(mios.get("version").get("number").asInt()).isEqualTo(2);
        assertThat(mios.get("version").get("introduction").asText()).startsWith("Al inscribirme en Formación RENASER,");
        JsonNode acuerdo = mios.get("agreement");
        assertThat(acuerdo.get("participantName").asText()).isEqualTo("Ana María Pérez Soto");
        assertThat(acuerdo.get("program").asText()).isEqualTo("Formación RENASER · 90 días");
        assertThat(acuerdo.get("startDate").asText()).isEqualTo("2026-11-03");
        assertThat(acuerdo.get("endDate").asText()).isEqualTo("2027-01-31");
        assertThat(acuerdo.get("started").asBoolean()).isFalse();
        assertThat(acuerdo.get("schedule").isNull()).as("pagó completo: la firma de siempre, sin cronograma").isTrue();
        // Las fechas de «Mi aceptación» son las de «Tu programa empieza mañana» (spec 036).
        assertThat(json.readTree(ok(enviar("GET", "/api/v1/onboarding/activate-program", sesion, null))).get("validStartDates").get(0).asText())
                .isEqualTo(acuerdo.get("startDate").asText());
    }

    // spec 039 FR-007
    @Test
    @DisplayName("sin cronograma, aceptar guarda lo que se mostró (programa, inicio y fin), no confirma ningún cronograma, y se puede releer y consultar")
    void aceptarSinCronogramaGuardaLoMostrado() throws Exception {
        UUID ana = aprendiz("ESCUELA", 90, "Ana María Pérez Soto", false);
        String sesion = sesionDe(ana);
        JsonNode mios = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));

        ok(enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", mios.get("version").get("id").asText(), "agreementStartDate", "2026-11-03")));

        Map<String, Object> constancia = jdbc.queryForMap("SELECT programa_mostrado, inicio_mostrado, fin_mostrado, cronograma_huella FROM renaser.aceptaciones_de_terminos "
                + "WHERE usuario_id = ?", ana);
        assertThat(constancia.get("programa_mostrado")).isEqualTo("Formación RENASER · 90 días");
        assertThat(constancia.get("inicio_mostrado").toString()).isEqualTo("2026-11-03");
        assertThat(constancia.get("fin_mostrado").toString()).isEqualTo("2027-01-31");
        assertThat(constancia.get("cronograma_huella")).isNull();
        assertThat(jdbc.queryForObject("SELECT cronograma_confirmado_en IS NULL FROM renaser.estado_onboarding WHERE usuario_id = ?", Boolean.class, ana))
                .as("sin cronograma, no hay confirmación").isTrue();

        JsonNode releido = json.readTree(ok(enviar("GET", TERMINOS + "/accepted", sesion, null)));
        assertThat(releido.get("agreement").get("program").asText()).isEqualTo("Formación RENASER · 90 días");
        assertThat(releido.get("agreement").get("startDate").asText()).isEqualTo("2026-11-03");
        assertThat(releido.get("agreement").get("endDate").asText()).isEqualTo("2027-01-31");
        assertThat(releido.get("agreement").get("scheduleSigned").asBoolean()).isFalse();

        JsonNode deAdministracion = json.readTree(ok(enviar("GET", "/api/v1/admin/terms/people/" + ana, sesionDe(usuario("ADMIN", "Ana Admin")), null)));
        assertThat(deAdministracion.get(0).get("program").asText()).isEqualTo("Formación RENASER · 90 días");
        assertThat(deAdministracion.get(0).get("startDate").asText()).isEqualTo("2026-11-03");
        assertThat(deAdministracion.get(0).get("scheduleSigned").asBoolean()).isFalse();
    }

    // spec 039 FR-004, SC-004
    @Test
    @DisplayName("el Diplomado de 3 meses, el de 1 año y la Certificación dicen su programa y su fin: 89, 89 y 364 días después del inicio")
    void duracionesPorFormacion() throws Exception {
        record Caso(String formacion, int dias, String programa, String fin) { }
        for (Caso caso : new Caso[] {new Caso("DIPLOMADO", 90, "Diplomado RENASER · 3 meses", "2027-01-31"),
                new Caso("DIPLOMADO", 365, "Diplomado RENASER · 1 año", "2027-11-02"),
                new Caso("CERTIFICACION", 90, "Certificación RENASER · 90 días", "2027-01-31")}) {
            String sesion = sesionDe(aprendiz(caso.formacion(), caso.dias(), "Beto Quispe", false));

            JsonNode acuerdo = json.readTree(ok(enviar("GET", TERMINOS, sesion, null))).get("agreement");

            assertThat(acuerdo.get("program").asText()).as("%s %d", caso.formacion(), caso.dias()).isEqualTo(caso.programa());
            assertThat(acuerdo.get("startDate").asText()).isEqualTo("2026-11-03");
            assertThat(acuerdo.get("endDate").asText()).as("%s %d", caso.formacion(), caso.dias()).isEqualTo(caso.fin());
        }
    }

    // spec 039 FR-007
    @Test
    @DisplayName("pasó la medianoche con la pantalla abierta: el inicio que vio ya no es mañana → 409 y no queda constancia; con la fecha nueva sí")
    void laMedianocheEnLaMitad() throws Exception {
        UUID ana = aprendiz("ESCUELA", 90, "Ana María Pérez Soto", false);
        String sesion = sesionDe(ana);
        JsonNode mios = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));
        String vista = mios.get("agreement").get("startDate").asText();
        assertThat(vista).isEqualTo("2026-11-03");

        reloj.fijar(PASADA_LA_MEDIANOCHE_EN_LIMA);
        HttpResponse<String> rechazada = enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", mios.get("version").get("id").asText(), "agreementStartDate", vista));

        assertThat(rechazada.statusCode()).as(rechazada.body()).isEqualTo(409);
        assertThat(rechazada.body()).contains("Pasó la medianoche");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ?", Integer.class, ana)).isZero();

        JsonNode releido = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));
        assertThat(releido.get("agreement").get("startDate").asText()).isEqualTo("2026-11-04");
        ok(enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", releido.get("version").get("id").asText(), "agreementStartDate", "2026-11-04")));
        assertThat(jdbc.queryForObject("SELECT inicio_mostrado::text FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ?", String.class, ana)).isEqualTo("2026-11-04");
    }

    // ── La persona: con cronograma ──────────────────────────────────────────────────────────────────────────────────

    // spec 039 FR-008, SC-002
    @Test
    @DisplayName("con cronograma: debajo de «Mi aceptación» llega el cronograma (moneda, cuotas y huella); al firmar queda confirmado con la MISMA confirmación de «Mi ingreso»")
    void conCronogramaSeFirmaConLoMismo() throws Exception {
        UUID ana = aprendiz("ESCUELA", 90, "Ana María Pérez Soto", false);
        String sesion = sesionDe(ana);
        asignarPago(cronograma("PEN", "2026-11-15", 500, "2026-12-15", 500), ana);
        JsonNode mios = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));
        JsonNode cronograma = mios.get("agreement").get("schedule");
        assertThat(cronograma.get("currency").asText()).isEqualTo("PEN");
        assertThat(cronograma.get("installments")).hasSize(2);
        assertThat(cronograma.get("installments").get(0).get("dueDate").asText()).isEqualTo("2026-11-15");
        assertThat(cronograma.get("installments").get(0).get("amount").decimalValue()).isEqualByComparingTo("500");
        String huella = cronograma.get("fingerprint").asText();
        assertThat(huella).isNotBlank();
        assertThat(json.readTree(ok(enviar("GET", "/api/v1/me/ingreso", sesion, null))).get("payments").get("confirmation").get("state").asText())
                .as("antes de firmar, «Mi ingreso» lo pide").isEqualTo("NOT_YET");

        ok(enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", mios.get("version").get("id").asText(), "agreementStartDate", "2026-11-03",
                "scheduleFingerprint", huella)));

        Map<String, Object> estado = jdbc.queryForMap("SELECT cronograma_confirmado_en, cronograma_confirmado_nombre, cronograma_confirmado_huella FROM renaser.estado_onboarding "
                + "WHERE usuario_id = ?", ana);
        assertThat(estado.get("cronograma_confirmado_en")).isNotNull();
        assertThat(estado.get("cronograma_confirmado_nombre")).as("el nombre completo del perfil").isEqualTo("Ana María Pérez Soto");
        assertThat(estado.get("cronograma_confirmado_huella")).isEqualTo(huella);
        assertThat(jdbc.queryForObject("SELECT cronograma_huella FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ?", String.class, ana)).isEqualTo(huella);
        JsonNode miIngreso = json.readTree(ok(enviar("GET", "/api/v1/me/ingreso", sesion, null)));
        assertThat(miIngreso.get("payments").get("confirmation").get("state").asText()).as("«Mi ingreso» ya no lo vuelve a pedir").isEqualTo("CONFIRMED");
        assertThat(miIngreso.get("payments").get("confirmation").get("name").asText()).isEqualTo("Ana María Pérez Soto");
        assertThat(json.readTree(ok(enviar("GET", TERMINOS + "/accepted", sesion, null))).get("agreement").get("scheduleSigned").asBoolean()).isTrue();
    }

    // spec 039 FR-008
    @Test
    @DisplayName("Administración cambió el cronograma mientras leía: 409, no queda aceptación ni confirmación; con la huella nueva sí")
    void elCronogramaCambioMientrasLeia() throws Exception {
        UUID ana = aprendiz("ESCUELA", 90, "Ana María Pérez Soto", false);
        String sesion = sesionDe(ana);
        asignarPago(cronograma("PEN", "2026-11-15", 500), ana);
        JsonNode mios = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));
        String huellaVieja = mios.get("agreement").get("schedule").get("fingerprint").asText();
        asignarPago(cronograma("PEN", "2026-11-15", 500, "2026-12-15", 500), ana);

        HttpResponse<String> rechazada = enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", mios.get("version").get("id").asText(),
                "scheduleFingerprint", huellaVieja));

        assertThat(rechazada.statusCode()).as(rechazada.body()).isEqualTo(409);
        assertThat(rechazada.body()).contains("Tu cronograma cambió");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ?", Integer.class, ana)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.estado_onboarding WHERE usuario_id = ? AND cronograma_confirmado_en IS NOT NULL", Integer.class, ana)).isZero();

        JsonNode releido = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));
        String huellaNueva = releido.get("agreement").get("schedule").get("fingerprint").asText();
        assertThat(huellaNueva).isNotEqualTo(huellaVieja);
        ok(enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", releido.get("version").get("id").asText(), "scheduleFingerprint", huellaNueva)));
        assertThat(jdbc.queryForObject("SELECT cronograma_confirmado_huella FROM renaser.estado_onboarding WHERE usuario_id = ?", String.class, ana)).isEqualTo(huellaNueva);
    }

    // spec 039 FR-009, FR-011
    @Test
    @DisplayName("aceptar sin la huella (una app anterior) con un cronograma asignado no lo confirma: «Mi ingreso» lo sigue pidiendo")
    void sinLaHuellaNoSeConfirmaNada() throws Exception {
        UUID ana = aprendiz("ESCUELA", 90, "Ana María Pérez Soto", false);
        String sesion = sesionDe(ana);
        asignarPago(cronograma("PEN", "2026-11-15", 500), ana);
        String version = json.readTree(ok(enviar("GET", TERMINOS, sesion, null))).get("version").get("id").asText();

        ok(enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", version)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.estado_onboarding WHERE usuario_id = ? AND cronograma_confirmado_en IS NOT NULL", Integer.class, ana)).isZero();
        assertThat(jdbc.queryForObject("SELECT cronograma_huella IS NULL FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ?", Boolean.class, ana)).isTrue();
        assertThat(json.readTree(ok(enviar("GET", "/api/v1/me/ingreso", sesion, null))).get("payments").get("confirmation").get("state").asText()).isEqualTo("NOT_YET");
    }

    @Test
    @DisplayName("si Administración cambia el cronograma DESPUÉS de firmado, «Mi ingreso» pide confirmarlo de nuevo, sin volver a firmar los términos")
    void despuesDeFirmadoVaLaReglaDeLa032() throws Exception {
        UUID ana = aprendiz("ESCUELA", 90, "Ana María Pérez Soto", false);
        String sesion = sesionDe(ana);
        asignarPago(cronograma("PEN", "2026-11-15", 500), ana);
        JsonNode mios = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));
        ok(enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", mios.get("version").get("id").asText(),
                "scheduleFingerprint", mios.get("agreement").get("schedule").get("fingerprint").asText())));

        ok(enviar("POST", PAGOS + ana + "/cuotas", sesionDe(usuario("ADMIN", "Ana Admin")), Map.of("dueDate", "2026-12-15", "amount", 500)));

        assertThat(json.readTree(ok(enviar("GET", "/api/v1/me/ingreso", sesion, null))).get("payments").get("confirmation").get("state").asText()).isEqualTo("OUTDATED");
        assertThat(json.readTree(ok(enviar("GET", TERMINOS, sesion, null))).get("pending").asText()).as("los términos siguen aceptados").isEqualTo("NONE");
    }

    // ── Quien ya estaba dentro ──────────────────────────────────────────────────────────────────────────────────────

    // spec 039 FR-006, FR-009
    @Test
    @DisplayName("volver a aceptar (ya estaba dentro): bloquea, trae su programa y fechas reales (ya empezó) y NO ofrece el cronograma aunque tenga uno; firmar no lo confirma")
    void volverAAceptar() throws Exception {
        UUID beto = aprendiz("ESCUELA", 90, "Beto Quispe", true);
        String sesion = sesionDe(beto);
        asignarPago(cronograma("PEN", "2026-11-15", 500), beto);
        // Había aceptado la versión 1 antes de que el dueño entregara el texto nuevo.
        jdbc.update("INSERT INTO renaser.aceptaciones_de_terminos (id, usuario_id, version_id, aceptada_en) VALUES (gen_random_uuid(), ?, ?::uuid, now())", beto,
                jdbc.queryForObject("SELECT id::text FROM renaser.terminos_versiones WHERE formacion = 'ESCUELA' AND numero = 1", String.class));

        JsonNode mios = json.readTree(ok(enviar("GET", TERMINOS, sesion, null)));

        assertThat(mios.get("pending").asText()).isEqualTo("NEW_VERSION");
        assertThat(mios.get("blocks").asBoolean()).isTrue();
        assertThat(mios.get("changeNote").asText()).isEqualTo("Nuevo Acuerdo de transformación");
        JsonNode acuerdo = mios.get("agreement");
        assertThat(acuerdo.get("started").asBoolean()).isTrue();
        assertThat(acuerdo.get("startDate").asText()).isEqualTo("2026-10-26");
        assertThat(acuerdo.get("endDate").asText()).isEqualTo("2027-01-23");
        assertThat(acuerdo.get("program").asText()).isEqualTo("Formación RENASER · 90 días");
        assertThat(acuerdo.get("schedule").isNull()).as("a quien ya estaba dentro se le pide en «Mi ingreso»").isTrue();

        ok(enviar("POST", HITOS, sesion, Map.of("milestone", "TERMINOS", "termsVersionId", mios.get("version").get("id").asText(), "agreementStartDate", "2026-10-26")));

        assertThat(jdbc.queryForObject("SELECT fin_mostrado::text FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ? AND programa_mostrado IS NOT NULL", String.class, beto))
                .isEqualTo("2027-01-23");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.estado_onboarding WHERE usuario_id = ? AND cronograma_confirmado_en IS NOT NULL", Integer.class, beto)).isZero();
        assertThat(json.readTree(ok(enviar("GET", "/api/v1/me/ingreso", sesion, null))).get("payments").get("confirmation").get("state").asText()).isEqualTo("NOT_YET");
    }

    @Test
    @DisplayName("el personal sin programa firma con su nombre y el programa de su formación, sin fechas: no se le promete un inicio")
    void elPersonalSinProgramaNoTieneFechas() throws Exception {
        UUID mentor = usuario("MENTOR", "Mario Mentor");
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, mentor, "MENTOR");

        JsonNode acuerdo = json.readTree(ok(enviar("GET", TERMINOS, sesionDe(mentor), null))).get("agreement");

        assertThat(acuerdo.get("participantName").asText()).isEqualTo("Mario Mentor");
        assertThat(acuerdo.get("program").asText()).isEqualTo("Formación RENASER");
        assertThat(acuerdo.get("startDate").isNull()).isTrue();
        assertThat(acuerdo.get("endDate").isNull()).isTrue();
        assertThat(acuerdo.get("schedule").isNull()).isTrue();
    }

    // ── Semilla y pedidos ───────────────────────────────────────────────────────────────────────────────────────────

    /** Corre la V162 tal cual la corre Flyway, sobre una conexión propia, y deja el {@code search_path} como estaba. */
    private void correrLaV162() throws Exception {
        String script = new String(new ClassPathResource(SCRIPT).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute((ConnectionCallback<Void>) conexion -> {
            try (Statement sentencia = conexion.createStatement()) {
                String anterior;
                try (var resultado = sentencia.executeQuery("SELECT current_setting('search_path')")) {
                    resultado.next();
                    anterior = resultado.getString(1);
                }
                try {
                    sentencia.execute(script);
                } finally {
                    try (var restaurar = conexion.prepareStatement("SELECT set_config('search_path', ?, false)")) {
                        restaurar.setString(1, anterior);
                        restaurar.execute();
                    }
                }
            }
            return null;
        });
    }

    /** El arreglo {@code text[]} de la base, como arreglo de Java. */
    private static String[] aArreglo(Object columna) {
        try {
            return (String[]) ((java.sql.Array) columna).getArray();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void asignarPago(Map<String, Object> pedido, UUID persona) throws Exception {
        String admin = sesionDe(usuario("ADMIN", "Ana Admin"));
        ok(enviar("PUT", PAGOS + persona, admin, pedido));
    }

    private static Map<String, Object> cronograma(String moneda, Object... fechasYMontos) {
        List<Map<String, Object>> cuotas = new ArrayList<>();
        for (int i = 0; i < fechasYMontos.length; i += 2) {
            cuotas.add(Map.of("dueDate", fechasYMontos[i], "amount", fechasYMontos[i + 1]));
        }
        return Map.of("mode", "SCHEDULE", "currency", moneda, "installments", cuotas);
    }

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')",
                id, id + "@acuerdo.test", nombre, rol);
        cuentas.add(id);
        return id;
    }

    /**
     * Un aprendiz de esa formación con su programa. Sin activar (el onboarding) la fila nace como al aprobar la cuenta; activado, empezó el 26 de octubre.
     */
    private UUID aprendiz(String formacion, int duracion, String nombre, boolean activado) {
        UUID id = usuario("APRENDIZ", nombre);
        if (activado) {
            jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone, formacion, duracion_dias) "
                    + "VALUES (?, DATE '2026-10-26', 5, TIMESTAMPTZ '2026-10-25 12:00:00+00', 'America/Lima', ?, ?)", id, formacion, duracion);
        } else {
            jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, formacion, duracion_dias) VALUES (?, 0, 'America/Lima', ?, ?)",
                    id, formacion, duracion);
        }
        return id;
    }

    private String ok(HttpResponse<String> respuesta) {
        assertThat(respuesta.statusCode()).as("%s", respuesta.body()).isEqualTo(200);
        return respuesta.body();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                .header("X-Auth-Token", sesion);
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json");
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
}

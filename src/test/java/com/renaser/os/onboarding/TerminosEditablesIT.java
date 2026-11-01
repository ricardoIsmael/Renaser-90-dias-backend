package com.renaser.os.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.TestcontainersConfiguration;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los términos y condiciones editables (spec 027, D-304) de punta a punta con Postgres y sesión real: la versión 1 que deja la migración, la constancia de qué
 * versión aceptó cada persona, editar y publicar con o sin pedir aceptar de nuevo, el aislamiento entre formaciones y la regla de que una formación sin términos
 * publicados no recibe a nadie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TerminosEditablesIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> cuentas = new ArrayList<>();

    @BeforeEach
    void partirDeLaMigracion() {
        TerminosDePrueba.restablecer(jdbc);
    }

    @AfterEach
    void limpiar() {
        cuentas.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        TerminosDePrueba.restablecer(jdbc);
    }

    // ── La migración ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SC-006: la migración deja las 23 cláusulas de siempre como la versión 1 PUBLICADA de la Escuela; el Diplomado y la Certificación, solo un borrador")
    void laMigracionDejaLaVersionUno() {
        assertThat(jdbc.queryForObject("SELECT cardinality(clausulas) FROM renaser.terminos_versiones WHERE formacion = 'ESCUELA' AND numero = 1", Integer.class)).isEqualTo(23);
        assertThat(jdbc.queryForList("SELECT formacion || ':' || estado || ':' || coalesce(numero::text, '-') FROM renaser.terminos_versiones ORDER BY formacion",
                String.class)).containsExactly("CERTIFICACION:BORRADOR:-", "DIPLOMADO:BORRADOR:-", "ESCUELA:PUBLICADA:1");
        assertThat(jdbc.queryForObject("SELECT titulo FROM renaser.terminos_versiones WHERE formacion = 'ESCUELA' AND numero = 1", String.class)).isEqualTo("Términos y condiciones");
    }

    @Test
    @DisplayName("una versión publicada no se edita ni se borra, ni desde la base")
    void laVersionPublicadaEsInmutable() {
        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.terminos_versiones SET titulo = 'Otro' WHERE formacion = 'ESCUELA' AND numero = 1")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM renaser.terminos_versiones WHERE formacion = 'ESCUELA' AND numero = 1")).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("SC-003: quien ya había aceptado antes de las versiones figura como aceptante de la versión 1, con su fecha y su firma de siempre")
    void loExistenteSeAtribuyeALaVersionUno() throws Exception {
        UUID ana = aprendiz("ESCUELA");
        jdbc.update("INSERT INTO renaser.estado_onboarding (usuario_id, terminos_aceptados_en) VALUES (?, TIMESTAMPTZ '2026-09-01 15:00:00+00')", ana);
        // La sentencia de V150, tal cual, para esta persona: lo existente al migrar es lo que ella dice.
        jdbc.update("""
                INSERT INTO renaser.aceptaciones_de_terminos (id, usuario_id, version_id, aceptada_en, firma_media_id)
                SELECT gen_random_uuid(), e.usuario_id, '00000000-0000-4000-8000-000000000148', e.terminos_aceptados_en, NULL
                FROM renaser.estado_onboarding e WHERE e.terminos_aceptados_en IS NOT NULL AND e.usuario_id = ?
                """, ana);

        JsonNode constancia = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/people/" + ana, sesionDe(usuario("ADMIN")), null)));
        assertThat(constancia).hasSize(1);
        assertThat(constancia.get(0).get("number").asInt()).isEqualTo(1);
        assertThat(constancia.get(0).get("formation").asText()).isEqualTo("ESCUELA");
        assertThat(constancia.get(0).get("acceptedAt").asText()).startsWith("2026-09-01T15:00");
    }

    // ── La persona ──────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un aprendiz de la Escuela lee los términos de la Escuela, con sus 23 cláusulas, y en su primer ingreso no se bloquea")
    void leeLosSuyos() throws Exception {
        String sesion = sesionDe(aprendiz("ESCUELA"));

        JsonNode mios = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesion, null)));

        assertThat(mios.get("formation").get("code").asText()).isEqualTo("ESCUELA");
        assertThat(mios.get("version").get("number").asInt()).isEqualTo(1);
        assertThat(mios.get("version").get("clauses")).hasSize(23);
        assertThat(mios.get("pending").asText()).isEqualTo("FIRST_TIME");
        assertThat(mios.get("blocks").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("aceptar deja la constancia de la versión y la hora; después no hay nada pendiente y puede releer lo que aceptó")
    void aceptarDejaConstancia() throws Exception {
        UUID ana = aprendiz("ESCUELA");
        String sesion = sesionDe(ana);
        String version = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesion, null))).get("version").get("id").asText();

        assertThat(enviar("POST", "/api/v1/onboarding/milestones", sesion, Map.of("milestone", "TERMINOS", "termsVersionId", version)).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ? AND version_id = ?::uuid", Integer.class, ana, version)).isEqualTo(1);
        assertThat(json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesion, null))).get("pending").asText()).isEqualTo("NONE");
        JsonNode aceptados = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms/accepted", sesion, null)));
        assertThat(aceptados.get("version").get("number").asInt()).isEqualTo(1);
        assertThat(aceptados.get("acceptedAt").asText()).isNotBlank();
        assertThat(aceptados.get("signatureUrl").isNull()).as("sin firma dibujada no hay enlace").isTrue();
        // P3: con la firma que dibujó al aceptar, la relectura trae su enlace de lectura (solo la suya).
        Long firma = jdbc.queryForObject("INSERT INTO renaser.medias_onboarding (usuario_id, flujo, clave_pregunta, clase, bucket, ruta_storage, mime) "
                + "VALUES (?, 'terminos', 'terms_signature', 'firma', 'renaser-test', ?, 'image/png') RETURNING id", Long.class, ana, "onboarding/" + ana + "/firma.png");
        jdbc.update("UPDATE renaser.aceptaciones_de_terminos SET firma_media_id = ? WHERE usuario_id = ?", firma, ana);
        JsonNode conFirma = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms/accepted", sesion, null)));
        assertThat(conFirma.get("signatureUrl").asText()).contains("firma.png");
        assertThat(conFirma.get("version").get("clauses")).hasSize(23);
        JsonNode constancia = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/people/" + ana, sesionDe(usuario("ADMIN")), null)));
        assertThat(constancia.get(0).get("number").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("P3: quien nunca aceptó unos términos no tiene nada que releer (404), y cada quien relee la versión que aceptó él, aunque haya una más nueva")
    void releerLoAceptado() throws Exception {
        UUID ana = aprendiz("ESCUELA");
        UUID beto = aprendiz("ESCUELA");
        String sesionDeAna = sesionDe(ana);
        assertThat(enviar("GET", "/api/v1/onboarding/terms/accepted", sesionDeAna, null).statusCode()).as("nunca aceptó").isEqualTo(404);
        String version = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDeAna, null))).get("version").get("id").asText();
        assertThat(enviar("POST", "/api/v1/onboarding/milestones", sesionDeAna, Map.of("milestone", "TERMINOS", "termsVersionId", version)).statusCode()).isEqualTo(200);

        String admin = sesionDe(usuario("ADMIN"));
        JsonNode vigente = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", admin, null))).get("current");
        assertThat(guardar(admin, "ESCUELA", "Términos nuevos", vigente.get("introduction").asText(), vigente.get("clauses"), vigente.get("checkboxText").asText(), 1)
                .statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/admin/terms/ESCUELA/publish", admin, Map.of("note", "Cambió el título", "asksNewAcceptance", false, "baseNumber", 1))
                .statusCode()).isEqualTo(200);

        JsonNode loDeAna = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms/accepted", sesionDeAna, null)));
        assertThat(loDeAna.get("version").get("number").asInt()).as("relee la que aceptó, no la nueva").isEqualTo(1);
        assertThat(enviar("GET", "/api/v1/onboarding/terms/accepted", sesionDe(beto), null).statusCode()).as("Beto nunca aceptó").isEqualTo(404);
    }

    @Test
    @DisplayName("FR-024: una app anterior (sin la versión en el pedido) acepta y la constancia queda contra la vigente de la Escuela")
    void laAppAnteriorAcepta() throws Exception {
        UUID ana = aprendiz("ESCUELA");

        assertThat(enviar("POST", "/api/v1/onboarding/milestones", sesionDe(ana), Map.of("milestone", "TERMINOS")).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT v.numero FROM renaser.aceptaciones_de_terminos a JOIN renaser.terminos_versiones v ON v.id = a.version_id WHERE a.usuario_id = ?",
                Integer.class, ana)).isEqualTo(1);
    }

    @Test
    @DisplayName("SC-008: si Administración publicó mientras la persona leía, aceptar la versión vieja es 409 y NO queda constancia contra la equivocada")
    void laVersionQueCambioMientrasLeia() throws Exception {
        UUID ana = aprendiz("ESCUELA");
        String sesionDeAna = sesionDe(ana);
        String vieja = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDeAna, null))).get("version").get("id").asText();
        publicarEscuelaNueva(sesionDe(usuario("ADMIN")), "Se agregó una cláusula", false);

        HttpResponse<String> respuesta = enviar("POST", "/api/v1/onboarding/milestones", sesionDeAna, Map.of("milestone", "TERMINOS", "termsVersionId", vieja));

        assertThat(respuesta.statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ?", Integer.class, ana)).isZero();
        // y con la versión nueva sí
        String nueva = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDeAna, null))).get("version").get("id").asText();
        assertThat(nueva).isNotEqualTo(vieja);
        assertThat(enviar("POST", "/api/v1/onboarding/milestones", sesionDeAna, Map.of("milestone", "TERMINOS", "termsVersionId", nueva)).statusCode()).isEqualTo(200);
    }

    // ── Publicar, con o sin pedir aceptar de nuevo ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publicar PIDIENDO aceptar de nuevo bloquea a quien ya aceptó, con la nota de lo que cambió; al aceptar la nueva ya no hay nada pendiente")
    void publicarPidiendoBloquea() throws Exception {
        UUID ana = aprendiz("ESCUELA");
        String sesionDeAna = sesionDe(ana);
        aceptarLaVigente(sesionDeAna);

        publicarEscuelaNueva(sesionDe(usuario("ADMIN")), "Cambió la política de reembolso", true);

        JsonNode mios = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDeAna, null)));
        assertThat(mios.get("pending").asText()).isEqualTo("NEW_VERSION");
        assertThat(mios.get("blocks").asBoolean()).isTrue();
        assertThat(mios.get("changeNote").asText()).isEqualTo("Cambió la política de reembolso");
        assertThat(mios.get("version").get("number").asInt()).isEqualTo(2);
        aceptarLaVigente(sesionDeAna);
        assertThat(json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDeAna, null))).get("pending").asText()).isEqualTo("NONE");
        // lo que aceptó antes sigue en su historial
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.aceptaciones_de_terminos WHERE usuario_id = ?", Integer.class, ana)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-017 y SC-005: publicar SIN pedirlo no interrumpe a nadie; quien aceptó la 1 sigue sin nada pendiente")
    void publicarSinPedirNoInterrumpe() throws Exception {
        String sesionDeAna = sesionDe(aprendiz("ESCUELA"));
        aceptarLaVigente(sesionDeAna);

        publicarEscuelaNueva(sesionDe(usuario("ADMIN")), "Una coma", false);

        JsonNode mios = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDeAna, null)));
        assertThat(mios.get("pending").asText()).isEqualTo("NONE");
        assertThat(mios.get("blocks").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("el borrador no lo ve nadie más: la persona sigue leyendo la vigente mientras Administración edita")
    void elBorradorEsPrivado() throws Exception {
        String admin = sesionDe(usuario("ADMIN"));
        String sesionDeAna = sesionDe(aprendiz("ESCUELA"));
        guardarBorradorEscuela(admin, "Cláusula nueva solo en el borrador", 1);

        JsonNode mios = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDeAna, null)));

        assertThat(mios.get("version").get("number").asInt()).isEqualTo(1);
        assertThat(mios.get("version").get("clauses")).hasSize(23);
        JsonNode detalle = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", admin, null)));
        assertThat(detalle.get("draft").get("clauses")).hasSize(24);
        assertThat(detalle.get("current").get("clauses")).hasSize(23);
    }

    @Test
    @DisplayName("no se publica sin cambios, sin borrador, con una cláusula vacía, ni con una base vencida")
    void lasReglasDePublicar() throws Exception {
        String admin = sesionDe(usuario("ADMIN"));
        assertThat(enviar("POST", "/api/v1/admin/terms/ESCUELA/publish", admin, Map.of("asksNewAcceptance", false, "baseNumber", 1)).statusCode()).as("sin borrador").isEqualTo(409);

        // el borrador igual a lo vigente: sin cambios
        JsonNode vigente = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", admin, null))).get("current");
        assertThat(guardar(admin, "ESCUELA", vigente.get("title").asText(), vigente.get("introduction").asText(), vigente.get("clauses"),
                vigente.get("checkboxText").asText(), 1).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/admin/terms/ESCUELA/publish", admin, Map.of("asksNewAcceptance", false, "baseNumber", 1)).statusCode()).as("sin cambios").isEqualTo(409);

        // una cláusula vacía se guarda pero no se publica
        assertThat(guardar(admin, "ESCUELA", "Términos", List.of("Una", ""), "Acepto", 1).statusCode()).isEqualTo(200);
        HttpResponse<String> vacia = enviar("POST", "/api/v1/admin/terms/ESCUELA/publish", admin, Map.of("asksNewAcceptance", false, "baseNumber", 1));
        assertThat(vacia.statusCode()).isEqualTo(400);
        assertThat(vacia.body()).contains("La cláusula 2 está vacía");

        // otra persona publicó antes: la base que yo tenía (1) ya no es la vigente (2)
        publicarEscuelaNueva(sesionDe(usuario("ALQUIMISTA")), "Otra persona", false);
        assertThat(guardar(admin, "ESCUELA", "Términos", List.of("Una"), "Acepto", 1).statusCode()).as("guardar con base vencida").isEqualTo(409);
        assertThat(enviar("POST", "/api/v1/admin/terms/ESCUELA/publish", admin, Map.of("asksNewAcceptance", false, "baseNumber", 1)).statusCode()).as("publicar con base vencida").isEqualTo(409);
    }

    @Test
    @DisplayName("el historial lleva cada versión con su autor, su nota y cuántos la aceptaron; una vieja se restaura como borrador, sin publicarse sola")
    void historialYRestaurar() throws Exception {
        String admin = sesionDe(usuario("ADMIN"));
        aceptarLaVigente(sesionDe(aprendiz("ESCUELA")));
        publicarEscuelaNueva(admin, "Segunda", false);

        JsonNode detalle = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", admin, null)));
        assertThat(detalle.get("versions")).hasSize(2);
        assertThat(detalle.get("versions").get(0).get("version").get("number").asInt()).as("la más nueva primero").isEqualTo(2);
        assertThat(detalle.get("versions").get(0).get("publishedByName").asText()).isEqualTo("Fixture ADMIN");
        assertThat(detalle.get("versions").get(1).get("acceptedCount").asInt()).as("la 1 la aceptó una persona").isEqualTo(1);

        JsonNode restaurado = json.readTree(paraLeer(enviar("POST", "/api/v1/admin/terms/ESCUELA/versions/1/restore", admin, null)));
        assertThat(restaurado.get("state").asText()).isEqualTo("BORRADOR");
        assertThat(restaurado.get("clauses")).hasSize(23);
        assertThat(json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", admin, null))).get("current").get("number").asInt()).as("no se publica sola").isEqualTo(2);
        assertThat(enviar("POST", "/api/v1/admin/terms/ESCUELA/versions/9/restore", admin, null).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("SC-009 y FR-018: quiénes aceptaron una versión y quiénes de la formación todavía faltan")
    void quienesAceptaronYFaltan() throws Exception {
        UUID ana = aprendiz("ESCUELA");
        UUID beto = aprendiz("ESCUELA");
        UUID diana = aprendiz("DIPLOMADO");
        aceptarLaVigente(sesionDe(ana));

        JsonNode quienes = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA/versions/1/acceptances", sesionDe(usuario("ADMIN")), null)));

        List<String> aceptaron = new ArrayList<>();
        quienes.get("accepted").forEach(p -> aceptaron.add(p.get("id").asText()));
        List<String> faltan = new ArrayList<>();
        quienes.get("missing").forEach(p -> faltan.add(p.get("id").asText()));
        assertThat(aceptaron).contains(ana.toString()).doesNotContain(beto.toString());
        assertThat(faltan).contains(beto.toString()).doesNotContain(ana.toString(), diana.toString());
    }

    // ── Cada formación, sus términos ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR-020 y SC-007: cambiar los términos del Diplomado no mueve los de la Escuela, y cada persona ve los de SU formación")
    void cadaFormacionConLosSuyos() throws Exception {
        String admin = sesionDe(usuario("ADMIN"));
        String delDiplomado = sesionDe(aprendiz("DIPLOMADO"));
        String delaEscuela = sesionDe(aprendiz("ESCUELA"));
        assertThat(guardar(admin, "DIPLOMADO", "Términos del Diplomado", List.of("Solo del Diplomado"), "Acepto el Diplomado", null).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/admin/terms/DIPLOMADO/publish", admin, Map.of("asksNewAcceptance", false)).statusCode()).isEqualTo(200);

        JsonNode diplomado = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", delDiplomado, null)));
        JsonNode escuela = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", delaEscuela, null)));

        assertThat(diplomado.get("formation").get("code").asText()).isEqualTo("DIPLOMADO");
        assertThat(diplomado.get("version").get("title").asText()).isEqualTo("Términos del Diplomado");
        assertThat(diplomado.get("version").get("clauses")).hasSize(1);
        assertThat(escuela.get("version").get("title").asText()).isEqualTo("Términos y condiciones");
        assertThat(escuela.get("version").get("clauses")).hasSize(23);
        assertThat(escuela.get("version").get("number").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("el nombre de la formación y su duración se escriben con marcadores y cada persona los ve con su valor (FR-009)")
    void losMarcadores() throws Exception {
        String admin = sesionDe(usuario("ADMIN"));
        UUID beto = aprendiz("DIPLOMADO", 365);
        assertThat(guardar(admin, "DIPLOMADO", "Términos de {formacion}", List.of("El programa de {formacion} dura {duracion}."), "Acepto", null).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/admin/terms/DIPLOMADO/publish", admin, Map.of("asksNewAcceptance", false)).statusCode()).isEqualTo(200);

        JsonNode version = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDe(beto), null))).get("version");

        assertThat(version.get("title").asText()).isEqualTo("Términos de Diplomado Renaser");
        assertThat(version.get("clauses").get(0).asText()).isEqualTo("El programa de Diplomado Renaser dura 1 año.");
    }

    @Test
    @DisplayName("Q4-A y Q11-A: quien ya aceptó los de la Escuela y trabaja en el Diplomado (personal asignado) debe aceptar los del Diplomado, y bloquea")
    void elPersonalAceptaLosDeCadaFormacion() throws Exception {
        String admin = sesionDe(usuario("ADMIN"));
        UUID mentor = usuario("MENTOR");
        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'ESCUELA', true)", mentor);
        aceptarLaVigente(sesionDe(mentor));
        assertThat(guardar(admin, "DIPLOMADO", "Términos del Diplomado", List.of("Solo del Diplomado"), "Acepto", null).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/admin/terms/DIPLOMADO/publish", admin, Map.of("asksNewAcceptance", false)).statusCode()).isEqualTo(200);

        jdbc.update("UPDATE renaser.formaciones_personal SET activa = false WHERE usuario_id = ?", mentor);
        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'DIPLOMADO', true)", mentor);

        JsonNode mios = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesionDe(mentor), null)));
        assertThat(mios.get("formation").get("code").asText()).isEqualTo("DIPLOMADO");
        assertThat(mios.get("pending").asText()).isEqualTo("FIRST_IN_FORMATION");
        assertThat(mios.get("blocks").asBoolean()).isTrue();
    }

    // ── Quién puede ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR-001: solo Administración y Alquimista editan; mentor, líder, aprendiz y una cuenta suspendida reciben 403 en todo")
    void soloLaAdministracion() throws Exception {
        UUID suspendido = usuario("ADMIN");
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", suspendido);
        List<String> sesiones = List.of(sesionDe(usuario("MENTOR")), sesionDe(usuario("LIDER_MENTORES")), sesionDe(usuario("GUIA_APRENDIZ")), sesionDe(aprendiz("ESCUELA")),
                sesionDe(suspendido));
        for (String sesion : sesiones) {
            assertThat(enviar("GET", "/api/v1/admin/terms", sesion, null).statusCode()).isEqualTo(403);
            assertThat(enviar("GET", "/api/v1/admin/terms/ESCUELA", sesion, null).statusCode()).isEqualTo(403);
            assertThat(guardar(sesion, "ESCUELA", "T", List.of("Una"), "Acepto", 1).statusCode()).isEqualTo(403);
            assertThat(enviar("POST", "/api/v1/admin/terms/ESCUELA/publish", sesion, Map.of("asksNewAcceptance", false, "baseNumber", 1)).statusCode()).isEqualTo(403);
            assertThat(enviar("DELETE", "/api/v1/admin/terms/ESCUELA/draft", sesion, null).statusCode()).isEqualTo(403);
            assertThat(enviar("GET", "/api/v1/admin/terms/people/" + UUID.randomUUID(), sesion, null).statusCode()).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.terminos_versiones WHERE formacion = 'ESCUELA'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("el resumen trae las tres formaciones: la Escuela con su versión 1 y las otras dos «por revisar»")
    void elResumen() throws Exception {
        JsonNode resumen = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms", sesionDe(usuario("ALQUIMISTA")), null)));

        assertThat(resumen).hasSize(3);
        assertThat(resumen.get(0).get("formation").asText()).isEqualTo("ESCUELA");
        assertThat(resumen.get(0).get("needsReview").asBoolean()).isFalse();
        assertThat(resumen.get(1).get("needsReview").asBoolean()).isTrue();
        assertThat(resumen.get(2).get("needsReview").asBoolean()).isTrue();
        assertThat(resumen.get(1).get("hasDraft").asBoolean()).isTrue();
    }

    // ── Semilla y pedidos ───────────────────────────────────────────────────────────────────────────────────────────

    private void publicarEscuelaNueva(String sesionDeAdmin, String nota, boolean pideNueva) throws Exception {
        int base = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", sesionDeAdmin, null))).get("current").get("number").asInt();
        JsonNode vigente = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", sesionDeAdmin, null))).get("current");
        List<String> clausulas = new ArrayList<>();
        vigente.get("clauses").forEach(c -> clausulas.add(c.asText()));
        clausulas.add("Cláusula agregada en la versión " + (base + 1));
        assertThat(guardar(sesionDeAdmin, "ESCUELA", vigente.get("title").asText(), clausulas, vigente.get("checkboxText").asText(), base).statusCode()).isEqualTo(200);
        assertThat(enviar("POST", "/api/v1/admin/terms/ESCUELA/publish", sesionDeAdmin, Map.of("note", nota, "asksNewAcceptance", pideNueva, "baseNumber", base)).statusCode())
                .isEqualTo(200);
    }

    private void guardarBorradorEscuela(String sesionDeAdmin, String clausulaNueva, int base) throws Exception {
        JsonNode vigente = json.readTree(paraLeer(enviar("GET", "/api/v1/admin/terms/ESCUELA", sesionDeAdmin, null))).get("current");
        List<String> clausulas = new ArrayList<>();
        vigente.get("clauses").forEach(c -> clausulas.add(c.asText()));
        clausulas.add(clausulaNueva);
        assertThat(guardar(sesionDeAdmin, "ESCUELA", vigente.get("title").asText(), clausulas, vigente.get("checkboxText").asText(), base).statusCode()).isEqualTo(200);
    }

    private void aceptarLaVigente(String sesion) throws Exception {
        String version = json.readTree(paraLeer(enviar("GET", "/api/v1/onboarding/terms", sesion, null))).get("version").get("id").asText();
        assertThat(enviar("POST", "/api/v1/onboarding/milestones", sesion, Map.of("milestone", "TERMINOS", "termsVersionId", version)).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> guardar(String sesion, String formacion, String titulo, Iterable<?> clausulas, String casilla, Integer base) throws Exception {
        return guardar(sesion, formacion, titulo, "", clausulas, casilla, base);
    }

    private HttpResponse<String> guardar(String sesion, String formacion, String titulo, String introduccion, Iterable<?> clausulas, String casilla, Integer base)
            throws Exception {
        List<Object> lista = new ArrayList<>();
        clausulas.forEach(lista::add);
        java.util.HashMap<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put("title", titulo);
        cuerpo.put("introduction", introduccion);
        cuerpo.put("clauses", lista.stream().map(c -> c instanceof JsonNode n ? n.asText() : String.valueOf(c)).toList());
        cuerpo.put("checkboxText", casilla);
        cuerpo.put("baseNumber", base);
        return enviar("PUT", "/api/v1/admin/terms/" + formacion + "/draft", sesion, cuerpo);
    }

    private UUID usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')",
                id, id + "@terminos.test", "Fixture " + rol, rol);
        cuentas.add(id);
        return id;
    }

    private UUID aprendiz(String formacion) {
        return aprendiz(formacion, 90);
    }

    private UUID aprendiz(String formacion, int duracion) {
        UUID id = usuario("APRENDIZ");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone, formacion, duracion_dias) "
                + "VALUES (?, DATE '2026-10-26', 5, TIMESTAMPTZ '2026-10-25 12:00:00+00', 'America/Lima', ?, ?)", id, formacion, duracion);
        return id;
    }

    private String paraLeer(HttpResponse<String> respuesta) {
        assertThat(respuesta.statusCode()).as("%s", respuesta.body()).isEqualTo(200);
        return respuesta.body();
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String sesion, Map<String, ?> cuerpo) throws Exception {
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

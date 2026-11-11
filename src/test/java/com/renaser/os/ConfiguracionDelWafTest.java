package com.renaser.os;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La plantilla del Web ACL de AWS WAF para CloudFront (spec 050 tramo B, FR-001 a FR-004). Es una prueba estática
 * sobre {@code infra/waf/web-acl.plantilla.json}: el recurso real vive en la cuenta de AWS del dueño (FR-005) y AWS
 * valida el esquema con {@code check-capacity} en el simulacro de {@code crear-web-acl.sh}.
 */
class ConfiguracionDelWafTest {

    private static final Path PLANTILLA = Path.of("infra/waf/web-acl.plantilla.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode plantilla() throws IOException {
        return JSON.readTree(Files.readString(PLANTILLA));
    }

    private static JsonNode regla(String nombre) throws IOException {
        for (JsonNode regla : plantilla().get("Rules")) {
            if (nombre.equals(regla.get("Name").asText())) {
                return regla;
            }
        }
        throw new AssertionError("la plantilla no tiene la regla " + nombre);
    }

    @Test
    // spec 050 FR-001
    @DisplayName("es un Web ACL de alcance CloudFront con dos reglas por tasa por IP en ventanas de 5 minutos: 2.000 en toda la API y 300 en /api/v1/auth/")
    void reglasPorTasa() throws IOException {
        assertThat(plantilla().get("Scope").asText()).isEqualTo("CLOUDFRONT");
        JsonNode global = regla("limite-global").get("Statement").get("RateBasedStatement");
        assertThat(global.get("Limit").asInt()).isEqualTo(2000);
        assertThat(global.get("EvaluationWindowSec").asInt()).isEqualTo(300);
        assertThat(global.get("AggregateKeyType").asText()).isEqualTo("IP");
        assertThat(global.has("ScopeDownStatement")).as("el límite global cuenta toda la API").isFalse();

        JsonNode auth = regla("limite-auth").get("Statement").get("RateBasedStatement");
        assertThat(auth.get("Limit").asInt()).isEqualTo(300);
        assertThat(auth.get("EvaluationWindowSec").asInt()).isEqualTo(300);
        assertThat(auth.get("AggregateKeyType").asText()).isEqualTo("IP");
        JsonNode ruta = auth.get("ScopeDownStatement").get("RegexMatchStatement");
        assertThat(ruta.get("RegexString").asText()).isEqualTo("^/api/v1/auth/");
        assertThat(ruta.get("FieldToMatch").has("UriPath")).isTrue();
    }

    @Test
    // spec 050 FR-001
    @DisplayName("lleva los tres grupos administrados de AWS: Common, Known bad inputs e IP reputation")
    void gruposAdministrados() throws IOException {
        Set<String> grupos = new HashSet<>();
        for (JsonNode regla : plantilla().get("Rules")) {
            JsonNode grupo = regla.path("Statement").path("ManagedRuleGroupStatement");
            if (!grupo.isMissingNode()) {
                assertThat(grupo.get("VendorName").asText()).isEqualTo("AWS");
                grupos.add(grupo.get("Name").asText());
            }
        }
        assertThat(grupos).containsExactlyInAnyOrder("AWSManagedRulesCommonRuleSet", "AWSManagedRulesKnownBadInputsRuleSet",
                "AWSManagedRulesAmazonIpReputationList");
    }

    @Test
    // spec 050 FR-002
    @DisplayName("toda regla nace en Count, las que bloquean no están en la plantilla y el bloqueo por tasa responde 429 con Retry-After")
    void todoNaceEnCount() throws IOException {
        JsonNode reglas = plantilla().get("Rules");
        assertThat(reglas).hasSize(5);
        for (JsonNode regla : reglas) {
            JsonNode accion = regla.has("Action") ? regla.get("Action") : regla.get("OverrideAction");
            assertThat(accion.has("Count")).as("la regla %s no nace en Count: %s", regla.get("Name").asText(), accion).isTrue();
            assertThat(accion.size()).as("una sola acción en %s", regla.get("Name").asText()).isEqualTo(1);
        }
        assertThat(plantilla().get("DefaultAction").has("Allow")).isTrue();

        JsonNode bloqueo = plantilla().get("Renaser").get("AccionDeBloqueoPorTasa").get("Block").get("CustomResponse");
        assertThat(bloqueo.get("ResponseCode").asInt()).isEqualTo(429);
        assertThat(bloqueo.get("ResponseHeaders").get(0).get("Name").asText()).isEqualTo("Retry-After");
        assertThat(Integer.parseInt(bloqueo.get("ResponseHeaders").get(0).get("Value").asText())).isPositive();
        JsonNode cuerpo = plantilla().get("CustomResponseBodies").get(bloqueo.get("CustomResponseBodyKey").asText());
        assertThat(cuerpo).as("la respuesta personalizada que pide el bloqueo no está definida").isNotNull();
        assertThat(cuerpo.get("ContentType").asText()).isEqualTo("APPLICATION_JSON");
        JsonNode contenido = JSON.readTree(cuerpo.get("Content").asText());
        assertThat(contenido.get("message").asText()).isEqualTo("Espera un momento y vuelve a intentarlo.");
        assertThat(contenido.get("codigo").asText()).isEqualTo("DEMASIADOS_PEDIDOS");
    }

    @Test
    // spec 050 FR-003
    @DisplayName("un falso positivo se resuelve excluyendo la regla por nombre, con motivo y fecha, y nunca apagando el grupo entero")
    void excepcionesPorNombre() throws IOException {
        JsonNode excepciones = plantilla().get("Renaser").get("Excepciones");
        assertThat(excepciones.isArray()).isTrue();
        List<String> grupos = new ArrayList<>();
        regla("reglas-comunes");
        plantilla().get("Rules").forEach(r -> grupos.add(r.get("Name").asText()));
        for (JsonNode excepcion : excepciones) {
            assertThat(grupos).contains(excepcion.get("Grupo").asText());
            assertThat(excepcion.get("Regla").asText()).isNotBlank();
            assertThat(excepcion.get("Motivo").asText()).as("toda excepción dice por qué").isNotBlank();
            assertThat(excepcion.get("Fecha").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        }
        // Las excepciones llegan a AWS como RuleActionOverrides en Count DENTRO de su grupo; ExcludedRules no se usa
        // y la plantilla no lleva ninguna anulación a mano que apague un grupo.
        assertThat(Files.readString(PLANTILLA)).doesNotContain("ExcludedRules").doesNotContain("RuleActionOverrides");
        String comun = Files.readString(Path.of("infra/waf/comun.sh"));
        assertThat(comun).contains("RuleActionOverrides").contains("ActionToUse: {Count: {}}").doesNotContain("ExcludedRules");
    }

    @Test
    // spec 050 FR-004
    @DisplayName("el registro va a CloudWatch Logs 14 días y censura x-auth-token, authorization y cookie")
    void registroCensurado() throws IOException {
        JsonNode registro = plantilla().get("Renaser").get("RegistroDeCloudWatch");
        assertThat(registro.get("GrupoDeRegistros").asText()).startsWith("aws-waf-logs-");
        assertThat(registro.get("RetencionEnDias").asInt()).isEqualTo(14);
        List<String> cabeceras = new ArrayList<>();
        registro.get("CabecerasCensuradas").forEach(c -> cabeceras.add(c.asText()));
        assertThat(cabeceras).containsExactlyInAnyOrder("x-auth-token", "authorization", "cookie");
        assertThat(cabeceras).as("WAF exige los nombres de cabecera en minúsculas").allSatisfy(c -> assertThat(c).isEqualTo(c.toLowerCase()));
        String crear = Files.readString(Path.of("infra/waf/crear-web-acl.sh"));
        assertThat(crear).contains("RedactedFields").contains("SingleHeader").contains("put-logging-configuration");
        assertThat(crear).as("el registro no incluye el cuerpo de los pedidos (WAF no lo guarda y no se pide)").doesNotContain("LogScope");
    }

    @Test
    @DisplayName("cada regla tiene prioridad única y una métrica con su mismo nombre (pasar-regla-a-bloqueo.sh lee esa métrica)")
    void prioridadesYMetricas() throws IOException {
        Set<Integer> prioridades = new HashSet<>();
        for (JsonNode regla : plantilla().get("Rules")) {
            assertThat(prioridades.add(regla.get("Priority").asInt())).as("prioridad repetida en %s", regla.get("Name").asText()).isTrue();
            assertThat(regla.get("VisibilityConfig").get("MetricName").asText()).isEqualTo(regla.get("Name").asText());
            assertThat(regla.get("VisibilityConfig").get("CloudWatchMetricsEnabled").asBoolean()).isTrue();
            assertThat(regla.get("VisibilityConfig").get("SampledRequestsEnabled").asBoolean()).isTrue();
        }
    }
}

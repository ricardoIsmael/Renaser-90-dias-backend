package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D-336: el contrato para renaseros deja solo lo de un participante y los esquemas que eso usa. */
class ContratoParaRenaserosTest {

    private static final String DOC = """
            {"openapi":"3.1.0","info":{"title":"Renaser 90 días — API","description":"base"},
             "tags":[{"name":"users · Admin"}],
             "servers":[{"url":"http://localhost:33543"}],
             "paths":{
               "/api/v1/home":{"get":{"x-permiso":"USE_APP","responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Home"}}}}}}},
               "/api/v1/habit-tracks/{id}/complete":{"post":{"x-permiso":"FOLLOW_OWN_PROGRAM"}},
               "/api/v1/admin/users":{"get":{"x-permiso":"USE_APP","responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Secreto"}}}}}}},
               "/api/v1/cohorts":{"post":{"x-permiso":"MANAGE_COHORTS"}},
               "/api/v1/auth/login":{"post":{"security":[]}},
               "/api/v1/webhooks/whatsapp":{"post":{"security":[]}},
               "/api/v1/sin-permiso":{"get":{}},
               "/api/v1/calendar/events/{id}/attendance":{"get":{"x-permiso":"USE_APP","description":"**Permiso:** `USE_APP` (alcance: quien creó el evento, ADMIN, ALCHEMIST o MENTOR_LEAD)"}},
               "/api/v1/calendar/audience/people":{"get":{"x-permiso":"USE_APP","description":"**Permiso:** `USE_APP` (alcance: ADMIN, el líder y el mentor; el aprendiz no)"}},
               "/api/v1/espejo-sombra":{"get":{"x-permiso":"USE_APP","description":"**Permiso:** `USE_APP` (alcance: el propio participante, su mentor asignado o ADMIN/ALCHEMIST)"}},
               "/api/v1/mixta":{"get":{"x-permiso":"USE_APP"},"delete":{"x-permiso":"MANAGE_ROLES"}}
             },
             "components":{"schemas":{
               "Home":{"properties":{"semaforo":{"$ref":"#/components/schemas/Semaforo"}}},
               "Semaforo":{"type":"object"},
               "Secreto":{"type":"object"}
             }}}""";

    private ObjectNode filtrado() throws Exception {
        return ContratoParaRenaseros.filtrar((ObjectNode) new ObjectMapper().readTree(DOC));
    }

    @Test
    @DisplayName("entra lo que un TRAINEE puede usar; lo del equipo, lo de personal y lo sin permiso, no")
    void soloLoDeUnParticipante() throws Exception {
        var rutas = filtrado().path("paths");
        assertThat(rutas.has("/api/v1/home")).isTrue();
        assertThat(rutas.has("/api/v1/habit-tracks/{id}/complete")).isTrue();
        assertThat(rutas.has("/api/v1/auth/login")).as("pública de cuenta").isTrue();
        assertThat(rutas.has("/api/v1/admin/users")).as("USE_APP pero ruta de personal").isFalse();
        assertThat(rutas.has("/api/v1/cohorts")).as("permiso que no tiene un TRAINEE").isFalse();
        assertThat(rutas.has("/api/v1/webhooks/whatsapp")).as("pública pero no de cuenta").isFalse();
        assertThat(rutas.has("/api/v1/sin-permiso")).as("lo que no se sabe no se publica").isFalse();
        assertThat(rutas.has("/api/v1/calendar/events/{id}/attendance")).as("alcance solo del equipo").isFalse();
        assertThat(rutas.has("/api/v1/calendar/audience/people")).as("«el aprendiz no»").isFalse();
        assertThat(rutas.has("/api/v1/espejo-sombra")).as("mixta: el propio participante también").isTrue();
        assertThat(rutas.path("/api/v1/mixta").has("get")).isTrue();
        assertThat(rutas.path("/api/v1/mixta").has("delete")).as("cada operación por separado").isFalse();
    }

    @Test
    @DisplayName("quedan los esquemas que usan las rutas que entraron (también los anidados) y ninguno más")
    void soloLosEsquemasUsados() throws Exception {
        var esquemas = filtrado().path("components").path("schemas");
        assertThat(esquemas.has("Home")).isTrue();
        assertThat(esquemas.has("Semaforo")).as("anidado en Home").isTrue();
        assertThat(esquemas.has("Secreto")).as("solo lo usaba una ruta de personal").isFalse();
    }

    @Test
    @DisplayName("el documento dice su alcance y no arrastra las etiquetas del documento completo")
    void diceSuAlcance() throws Exception {
        var doc = filtrado();
        assertThat(doc.path("info").path("title").asText()).isEqualTo("Renaser — API para renaseros");
        assertThat(doc.path("info").path("description").asText()).contains("rol TRAINEE");
        assertThat(doc.has("tags")).isFalse();
        assertThat(doc.has("servers")).as("sin el localhost de la prueba").isFalse();
    }
}

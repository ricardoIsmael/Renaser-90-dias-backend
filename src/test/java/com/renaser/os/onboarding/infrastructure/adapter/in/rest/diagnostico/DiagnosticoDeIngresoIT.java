package com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta contra Postgres, Redis y la cadena HTTP completa (spec 026): Administración revisa el borrador sembrado, no puede publicarlo con la declaración
 * de relleno, la cambia y lo publica; le asigna el diagnóstico a una aprendiz que ya existía; ella lo responde y lo termina; de sus respuestas nacen sus objetivos de
 * 90 días «por confirmar» en Plan; los confirma una vez y quedan fijos; el Mapa ya no se escribe. Un mentor no administra las fichas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DiagnosticoDeIngresoIT {

    private static final String CLAVE = "ClaveDelDiagnosticoIT-2026!";
    private static final String ADMIN = "/api/v1/admin/diagnostico";
    private static final String MIO = "/api/v1/diagnostico/mio";

    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private SessionRepository<? extends Session> sesiones;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void limpiar() {
        tokens.forEach(sesiones::deleteById);
        usuarios.reversed().forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("del borrador sembrado a los objetivos confirmados en Plan")
    void delBorradorALosObjetivos() throws Exception {
        UUID aprendizId = UUID.randomUUID();
        String admin = sesion(UUID.randomUUID(), "ADMIN", false);
        String aprendiz = sesion(aprendizId, "APRENDIZ", true);
        String mentor = sesion(UUID.randomUUID(), "MENTOR", false);

        // 1. El borrador sembrado: portada y seis secciones, 31 preguntas; no se puede publicar con la declaración de relleno
        JsonNode borrador = ok(pedir("GET", ADMIN, admin, null));
        assertThat(borrador.get("secciones")).hasSize(7);
        assertThat(borrador.get("totales").get("preguntas").asInt()).isEqualTo(31);
        assertThat(borrador.get("puedePublicar").get("ok").asBoolean()).isFalse();
        assertThat(borrador.get("publicada").isNull()).isTrue();
        assertThat(pedir("POST", ADMIN + "/publicar", admin, Map.of("versionBase", 0)).statusCode()).isEqualTo(400);

        // 2. Un mentor no administra las fichas
        assertThat(pedir("GET", ADMIN, mentor, null).statusCode()).isEqualTo(403);

        // 3. Administración escribe la declaración y publica
        JsonNode consentimiento = preguntaDeClave(borrador, "diag_p23");
        assertThat(consentimiento.get("protegida").asBoolean()).isTrue();
        ObjectNode cambio = (ObjectNode) json.readTree(json.writeValueAsString(solicitud(consentimiento)));
        cambio.put("ayuda", "Declaro que lo que respondo es verdad y autorizo a Renaser a usarlo para acompañarme.");
        ok(pedir("PUT", ADMIN + "/preguntas/" + consentimiento.get("id").asInt(), admin, cambio));
        JsonNode publicada = ok(pedir("POST", ADMIN + "/publicar", admin, Map.of("versionBase", 0)));
        assertThat(publicada.get("version").asInt()).isEqualTo(1);
        assertThat(pedir("POST", ADMIN + "/publicar", admin, Map.of("versionBase", 0)).statusCode()).isEqualTo(409);
        assertThat(pedir("POST", ADMIN + "/publicar", admin, Map.of("versionBase", 1)).statusCode()).isEqualTo(409);

        // 4. Lo protegido no se quita; una pregunta de la que otras dependen, tampoco sola
        assertThat(pedir("POST", ADMIN + "/preguntas/quitar", admin, Map.of("ids", List.of(consentimiento.get("id").asInt()))).statusCode()).isEqualTo(409);
        int tieneNegocio = preguntaDeClave(borrador, "diag_p13").get("id").asInt();
        HttpResponse<String> conDependientes = pedir("POST", ADMIN + "/preguntas/quitar", admin, Map.of("ids", List.of(tieneNegocio)));
        assertThat(conDependientes.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conDependientes.body()).get("codigo").asText()).isEqualTo("TIENE_DEPENDIENTES");
        assertThat(json.readTree(conDependientes.body()).get("dependientes")).hasSize(4);

        // 5. Antes de asignarle el diagnóstico, la aprendiz tiene la ficha estándar
        assertThat(ok(pedir("GET", MIO + "/estado", aprendiz, null)).get("estado").asText()).isEqualTo("NO_APLICA");
        assertThat(pedir("GET", MIO, aprendiz, null).statusCode()).isEqualTo(404);

        // 6. Administración se lo asigna (026-9 B)
        JsonNode asignadas = ok(pedir("POST", ADMIN + "/personas/ficha", admin, Map.of("ids", List.of(aprendizId.toString()), "ficha", "CON_DIAGNOSTICO")));
        assertThat(asignadas.get("asignadas").asInt()).isEqualTo(1);
        assertThat(ok(pedir("GET", MIO + "/estado", aprendiz, null)).get("estado").asText()).isEqualTo("PENDIENTE");

        // 7. Ella lo abre (con su versión y el plazo de su formación) y lo responde
        JsonNode diagnostico = ok(pedir("GET", MIO, aprendiz, null));
        assertThat(diagnostico.get("version").asInt()).isEqualTo(1);
        assertThat(diagnostico.get("duracion").get("texto").asText()).isEqualTo("90 días");
        assertThat(diagnostico.get("portada").get("mensaje").asText()).contains("respuestas verdaderas");
        assertThat(textoDe(diagnostico, "diag_p19")).contains("90 días").doesNotContain("{duracion}");
        assertThat(preguntaDeClave(diagnostico, "diag_p1").get("precarga").asText()).isEqualTo("Persona IT");
        ok(pedir("PUT", MIO + "/respuestas", aprendiz, Map.of("respuestas", respuestasDe(diagnostico))));

        // 8. Lo termina: de sus respuestas nacen tres objetivos, el de su prioridad primero
        JsonNode fin = ok(pedir("POST", MIO + "/terminar", aprendiz, null));
        assertThat(fin.get("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(fin.get("prioridad").asText()).isEqualTo("TRABAJO");
        assertThat(fin.get("objetivos")).hasSize(3);
        assertThat(fin.get("objetivos").get(0).get("eje").asText()).isEqualTo("TRABAJO");
        assertThat(ok(pedir("GET", MIO + "/resumen", aprendiz, null)).get("primeraAccion").asText()).isEqualTo("Respuesta de prueba");
        assertThat(pedir("GET", MIO, aprendiz, null).statusCode()).isEqualTo(409);

        // 9. En Plan llegan «por confirmar»; se reescriben una vez y quedan fijos
        JsonNode rocas = ok(pedir("GET", "/api/v1/rocks/master", aprendiz, null));
        assertThat(rocas).hasSize(3);
        rocas.forEach(roca -> {
            assertThat(roca.get("origin").asText()).isEqualTo("DIAGNOSTICO");
            assertThat(roca.get("confirmed").asBoolean()).isFalse();
        });
        JsonNode confirmada = ok(pedir("PUT", "/api/v1/rocks/master/CUERPO", aprendiz, Map.of("objetivo", "Entrenar cuatro días por semana")));
        assertThat(confirmada.get("confirmed").asBoolean()).isTrue();
        assertThat(confirmada.get("objetivo").asText()).isEqualTo("Entrenar cuatro días por semana");
        HttpResponse<String> fijo = pedir("PUT", "/api/v1/rocks/master/CUERPO", aprendiz, Map.of("objetivo", "Otra cosa"));
        assertThat(fijo.statusCode()).isEqualTo(409);
        assertThat(json.readTree(fijo.body()).get("codigo").asText()).isEqualTo("ROCA_MAESTRA_FIJA");

        // 10. Administración lee su diagnóstico y el Mapa ya no se escribe
        JsonNode lectura = ok(pedir("GET", ADMIN + "/personas/" + aprendizId, admin, null));
        assertThat(lectura.get("estado").asText()).isEqualTo("COMPLETADO");
        assertThat(lectura.get("secciones")).isNotEmpty();
        assertThat(ok(pedir("GET", "/api/v1/mapa-renacimiento", aprendiz, null)).get("stageCompleted").asBoolean()).isTrue();
        HttpResponse<String> mapa = pedir("POST", "/api/v1/mapa-renacimiento/completar", aprendiz, null);
        assertThat(mapa.statusCode()).isEqualTo(409);
        assertThat(json.readTree(mapa.body()).get("codigo").asText()).isEqualTo("MAPA_RETIRADO");

        // 11. Quitar una pregunta que ya se publicó la retira: no se borra ni se pierden sus respuestas
        JsonNode quitada = ok(pedir("POST", ADMIN + "/preguntas/quitar", admin, Map.of("ids", List.of(tieneNegocio), "quitarDependientes", true)));
        assertThat(quitada.get("eliminadas").asInt()).isZero();
        assertThat(quitada.get("retiradas").asInt()).isEqualTo(5);
        assertThat(ok(pedir("GET", ADMIN, admin, null)).get("retiradas")).hasSize(5);
        assertThat(ok(pedir("GET", ADMIN + "/personas/" + aprendizId, admin, null)).get("secciones")).isNotEmpty();
    }

    // ------------------------------------------------------------------------------------------------ ayudas

    /** Responde lo mínimo de cada pregunta según su tipo y su destino. */
    private List<Map<String, Object>> respuestasDe(JsonNode diagnostico) {
        List<Map<String, Object>> respuestas = new ArrayList<>();
        for (JsonNode seccion : diagnostico.get("secciones")) {
            for (JsonNode p : seccion.get("preguntas")) {
                Map<String, Object> r = new java.util.LinkedHashMap<>();
                r.put("preguntaId", p.get("id").asInt());
                switch (p.get("tipo").asText()) {
                    case "TEXTO", "AREA_TEXTO" -> r.put("valorTexto", "Respuesta de prueba");
                    case "NUMERO" -> r.put("valorNumero", 34);
                    case "SELECCION_UNICA" -> r.put("valorTexto", valorDeLaOpcion(p));
                    case "CUADRICULA" -> r.put("valorJson", filasConPuntaje(p));
                    case "CASILLA" -> r.put("valorBooleano", true);
                    default -> { }
                }
                if (r.size() > 1) {
                    respuestas.add(r);
                }
            }
        }
        return respuestas;
    }

    private static String valorDeLaOpcion(JsonNode p) {
        String destino = p.get("alimenta").isNull() ? "" : p.get("alimenta").asText();
        for (JsonNode o : p.get("opciones")) {
            if (destino.equals("PRIORIDAD_PRINCIPAL") && o.get("valor").asText().equals("TRABAJO")) {
                return "TRABAJO";
            }
        }
        return p.get("opciones").get(p.get("opciones").size() - 1).get("valor").asText();
    }

    private String filasConPuntaje(JsonNode p) {
        ObjectNode filas = json.createObjectNode();
        p.get("opciones").forEach(o -> filas.put(o.get("valor").asText(), 3));
        return filas.toString();
    }

    private static JsonNode preguntaDeClave(JsonNode raiz, String clave) {
        for (JsonNode seccion : raiz.get("secciones")) {
            for (JsonNode p : seccion.get("preguntas")) {
                if (clave.equals(p.get("clave").asText())) {
                    return p;
                }
            }
        }
        throw new AssertionError("No está la pregunta " + clave);
    }

    private static String textoDe(JsonNode raiz, String clave) {
        return preguntaDeClave(raiz, clave).get("texto").asText();
    }

    /** El cuerpo para cambiar una pregunta tal como está (la pantalla manda la pregunta entera). */
    private Map<String, Object> solicitud(JsonNode p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("seccionId", p.get("seccionId").asInt());
        m.put("texto", p.get("texto").asText());
        m.put("ayuda", p.get("ayuda").isNull() ? null : p.get("ayuda").asText());
        m.put("tipo", p.get("tipo").asText());
        m.put("requerida", p.get("requerida").asBoolean());
        m.put("opciones", json.convertValue(p.get("opciones"), List.class));
        return m;
    }

    private String sesion(UUID id, String rol, boolean conPrograma) throws Exception {
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, hash_contrasena)
                VALUES (?, ?, 'Persona IT', CAST(? AS renaser.rol_usuario), 'ACTIVO', ?)
                """, id, id + "@renaser.test", rol, encoder.encode(CLAVE));
        usuarios.add(id);
        if (conPrograma) {
            jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, programa_activado_en) VALUES (?, 10, now())", id);
        }
        HttpResponse<String> login = pedir("POST", "/api/v1/auth/login", null, Map.of("email", id + "@renaser.test", "contrasena", CLAVE));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        String token = login.headers().firstValue("X-Auth-Token").orElseThrow();
        tokens.add(token);
        return token;
    }

    private JsonNode ok(HttpResponse<String> respuesta) throws Exception {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String token, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        if (token != null) {
            pedido.header("X-Auth-Token", token);
        }
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json").method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }
}

package com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La lectura de las cuotas de todos (D-338) de punta a punta contra Postgres, Redis y la cadena HTTP completa: varias personas en una llamada, el filtro de formación,
 * el de fechas y estado, la paginación y el 403 de quien no es de Administración. Misma configuración de Spring que {@link CronogramaDePagosIT} (comparten contexto y
 * base): solo se miran las personas que siembra cada prueba, y las fechas van en 2031 para que ningún dato de otra clase caiga en los filtros.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CuotasDeTodosIT {

    private static final String TODAS = "/api/v1/admin/pagos/cuotas";
    private static final String UNA = "/api/v1/admin/pagos/personas/";

    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SessionRepository<? extends Session> sesiones;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();

    private Cuenta ana;
    private Cuenta escuela;
    private Cuenta diplomado;
    private Cuenta certificacion;
    private Cuenta pagoCompleto;

    /** Tres personas con cronograma, una de cada formación, y una que pagó completo (sin cronograma: no sale). */
    @BeforeEach
    void sembrar() throws Exception {
        ana = cuenta("ADMIN", null);
        escuela = cuenta("APRENDIZ", "ESCUELA");
        diplomado = cuenta("APRENDIZ", "DIPLOMADO");
        certificacion = cuenta("APRENDIZ", "CERTIFICACION");
        pagoCompleto = cuenta("APRENDIZ", "ESCUELA");
        ok(pedir("PUT", UNA + escuela.id, ana.token, cronograma("USD", "2031-10-15", "2031-11-15", "2031-12-15")));
        ok(pedir("PUT", UNA + diplomado.id, ana.token, cronograma("PEN", "2031-11-20")));
        ok(pedir("PUT", UNA + certificacion.id, ana.token, cronograma("PEN", "2032-01-10")));
        ok(pedir("PUT", UNA + pagoCompleto.id, ana.token, Map.of("mode", "NONE")));
    }

    @AfterEach
    void limpiar() {
        tokens.forEach(sesiones::deleteById);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.cuotas_de_pago WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.estado_onboarding WHERE usuario_id = ?", id));
        usuarios.reversed().forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("varias personas en una sola llamada, con el id de /admin/trainees, su nombre, su correo y las cuotas con los campos de la ficha; quien pagó completo no sale")
    void variasPersonasEnUnaLlamada() throws Exception {
        String primera = idsDe(ok(pedir("GET", UNA + escuela.id, ana.token, null))).getFirst();
        ok(pedir("PUT", UNA + escuela.id + "/cuotas/" + primera + "/estado", ana.token, Map.of("status", "PAID")));

        JsonNode pagina = ok(pedir("GET", TODAS + "?size=200", ana.token, null));
        Map<String, JsonNode> filas = porPersona(pagina);

        assertThat(filas).containsKeys(escuela.id.toString(), diplomado.id.toString(), certificacion.id.toString());
        assertThat(filas).doesNotContainKey(pagoCompleto.id.toString());
        JsonNode deEscuela = filas.get(escuela.id.toString());
        assertThat(deEscuela.get("user").get("name").asText()).isEqualTo("Fixture APRENDIZ");
        assertThat(deEscuela.get("user").get("email").asText()).isEqualTo(escuela.id + "@renaser.test");
        assertThat(deEscuela.get("schedule").get("mode").asText()).isEqualTo("SCHEDULE");
        assertThat(deEscuela.get("schedule").get("currency").asText()).isEqualTo("USD");
        assertThat(deEscuela.get("schedule").get("confirmation").get("state").asText()).isEqualTo("NOT_YET");
        JsonNode cuotas = deEscuela.get("schedule").get("installments");
        assertThat(cuotas).extracting(c -> c.get("dueDate").asText()).containsExactly("2031-10-15", "2031-11-15", "2031-12-15");
        assertThat(cuotas.get(0).get("id").asText()).isEqualTo(primera);
        assertThat(cuotas.get(0).get("amount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(cuotas.get(0).get("status").asText()).isEqualTo("PAID");
        assertThat(cuotas.get(0).get("statusChangedAt").isNull()).isFalse();
        assertThat(cuotas.get(0).get("statusChangedByName").asText()).isEqualTo("Fixture ADMIN");
        assertThat(cuotas.get(1).get("status").asText()).isEqualTo("PENDING");
        assertThat(filas.get(diplomado.id.toString()).get("schedule").get("currency").asText()).isEqualTo("PEN");
        assertThat(pagina.get("page").asInt()).isZero();
        assertThat(pagina.get("size").asInt()).isEqualTo(200);
        assertThat(pagina.get("total").asLong()).isGreaterThanOrEqualTo(3);

        // la lectura de todos dice lo mismo que la ficha de cada persona
        JsonNode ficha = ok(pedir("GET", UNA + escuela.id, ana.token, null));
        assertThat(deEscuela.get("schedule")).isEqualTo(ficha.get("payments"));
    }

    @Test
    @DisplayName("formation: solo las personas de esa formación, como en /admin/trainees; vacío = todas; una formación que no existe es 400")
    void filtroDeFormacion() throws Exception {
        Map<String, JsonNode> delDiplomado = porPersona(ok(pedir("GET", TODAS + "?size=200&formation=DIPLOMADO", ana.token, null)));
        assertThat(delDiplomado).containsKey(diplomado.id.toString()).doesNotContainKeys(escuela.id.toString(), certificacion.id.toString());

        Map<String, JsonNode> deLaEscuela = porPersona(ok(pedir("GET", TODAS + "?size=200&formation=escuela", ana.token, null)));
        assertThat(deLaEscuela).containsKey(escuela.id.toString()).doesNotContainKeys(diplomado.id.toString(), certificacion.id.toString());

        assertThat(porPersona(ok(pedir("GET", TODAS + "?size=200&formation=", ana.token, null))))
                .containsKeys(escuela.id.toString(), diplomado.id.toString(), certificacion.id.toString());
        assertThat(pedir("GET", TODAS + "?formation=OTRA", ana.token, null).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("desde/hasta (inclusivos) y status recortan las cuotas y dejan fuera a quien no tiene ninguna que cumpla; un valor mal formado es 400")
    void filtroDeFechasYEstado() throws Exception {
        String deNoviembre = idsDe(ok(pedir("GET", UNA + escuela.id, ana.token, null))).get(1);
        ok(pedir("PUT", UNA + escuela.id + "/cuotas/" + deNoviembre + "/estado", ana.token, Map.of("status", "PAID")));

        JsonNode noviembre = ok(pedir("GET", TODAS + "?size=200&desde=2031-11-01&hasta=2031-11-20", ana.token, null));
        Map<String, JsonNode> filas = porPersona(noviembre);
        assertThat(filas).containsOnlyKeys(escuela.id.toString(), diplomado.id.toString());
        assertThat(noviembre.get("total").asLong()).isEqualTo(2);
        assertThat(filas.get(escuela.id.toString()).get("schedule").get("installments")).extracting(c -> c.get("dueDate").asText()).containsExactly("2031-11-15");
        assertThat(filas.get(diplomado.id.toString()).get("schedule").get("installments")).extracting(c -> c.get("dueDate").asText()).containsExactly("2031-11-20");

        Map<String, JsonNode> pagadas = porPersona(ok(pedir("GET", TODAS + "?size=200&desde=2031-01-01&status=PAID", ana.token, null)));
        assertThat(pagadas).containsOnlyKeys(escuela.id.toString());
        assertThat(pagadas.get(escuela.id.toString()).get("schedule").get("installments")).singleElement()
                .satisfies(c -> assertThat(c.get("id").asText()).isEqualTo(deNoviembre));

        Map<String, JsonNode> pendientes = porPersona(ok(pedir("GET", TODAS + "?size=200&desde=2031-01-01&status=PENDING", ana.token, null)));
        assertThat(pendientes.get(escuela.id.toString()).get("schedule").get("installments")).extracting(c -> c.get("dueDate").asText())
                .containsExactly("2031-10-15", "2031-12-15");

        assertThat(pedir("GET", TODAS + "?desde=15-11-2031", ana.token, null).statusCode()).isEqualTo(400);
        assertThat(pedir("GET", TODAS + "?status=VENCIDA", ana.token, null).statusCode()).isEqualTo(400);
        assertThat(pedir("GET", TODAS + "?desde=2031-12-01&hasta=2031-11-01", ana.token, null).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("paginación: cada página trae a lo más size personas, sin repetir ni saltear a nadie, con el mismo total; size fuera de 1..200 es 400")
    void paginacion() throws Exception {
        String rango = "&desde=2031-10-01&hasta=2032-01-31";
        JsonNode primera = ok(pedir("GET", TODAS + "?page=0&size=2" + rango, ana.token, null));
        JsonNode segunda = ok(pedir("GET", TODAS + "?page=1&size=2" + rango, ana.token, null));
        JsonNode tercera = ok(pedir("GET", TODAS + "?page=2&size=2" + rango, ana.token, null));

        assertThat(primera.get("total").asLong()).isEqualTo(3);
        assertThat(segunda.get("total").asLong()).isEqualTo(3);
        assertThat(primera.get("content")).hasSize(2);
        assertThat(segunda.get("content")).hasSize(1);
        assertThat(tercera.get("content")).isEmpty();
        Set<String> vistas = new HashSet<>(porPersona(primera).keySet());
        vistas.addAll(porPersona(segunda).keySet());
        assertThat(vistas).containsExactlyInAnyOrder(escuela.id.toString(), diplomado.id.toString(), certificacion.id.toString());

        assertThat(pedir("GET", TODAS + "?size=201", ana.token, null).statusCode()).isEqualTo(400);
        assertThat(pedir("GET", TODAS + "?size=0", ana.token, null).statusCode()).isEqualTo(400);
        assertThat(pedir("GET", TODAS + "?page=-1", ana.token, null).statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("solo Administración y Alquimista: mentor, guía, líder, Soporte TI y aprendiz reciben 403, una Administración suspendida también, y sin sesión es 403 como el resto de la API")
    void soloAdministracionYAlquimista() throws Exception {
        Cuenta alquimista = cuenta("ALQUIMISTA", null);
        assertThat(porPersona(ok(pedir("GET", TODAS + "?size=200", alquimista.token, null)))).containsKey(escuela.id.toString());

        for (String rol : List.of("MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES", "SOPORTE_TI", "APRENDIZ")) {
            Cuenta intruso = cuenta(rol, "ESCUELA");
            assertThat(pedir("GET", TODAS, intruso.token, null).statusCode()).as("%s lee las cuotas de todos", rol).isEqualTo(403);
        }
        assertThat(pedir("GET", TODAS, null, null).statusCode()).as("sin sesión").isEqualTo(403);

        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", alquimista.id);
        assertThat(pedir("GET", TODAS, alquimista.token, null).statusCode()).as("suspendida").isEqualTo(403);
    }

    // ------------------------------------------------------------------------------------------ ayudas

    private record Cuenta(UUID id, String token) {
    }

    /** Una cuenta con su sesión; los aprendices con programa en su formación y el personal con la Escuela asignada. */
    private Cuenta cuenta(String rol, String formacion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Fixture " + rol, rol);
        usuarios.add(id);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        if ("APRENDIZ".equals(rol) && formacion != null) {
            jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, programa_activado_en, formacion) VALUES (?, 10, now(), ?)", id, formacion);
        }
        String token = guardarSesion(sesiones, id);
        tokens.add(token);
        return new Cuenta(id, token);
    }

    private static <S extends Session> String guardarSesion(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, List.of()));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        repositorio.save(sesion);
        return sesion.getId();
    }

    private static Map<String, Object> cronograma(String moneda, String... fechas) {
        List<Map<String, Object>> cuotas = new ArrayList<>();
        for (String fecha : fechas) {
            cuotas.add(Map.of("dueDate", fecha, "amount", 1000));
        }
        return Map.of("mode", "SCHEDULE", "currency", moneda, "installments", cuotas);
    }

    private static List<String> idsDe(JsonNode ficha) {
        List<String> ids = new ArrayList<>();
        ficha.get("payments").get("installments").forEach(c -> ids.add(c.get("id").asText()));
        return ids;
    }

    /** Las filas de la página por {@code user.id}; falla si una persona sale dos veces. */
    private static Map<String, JsonNode> porPersona(JsonNode pagina) {
        Map<String, JsonNode> filas = new HashMap<>();
        pagina.get("content").forEach(fila -> assertThat(filas.put(fila.get("user").get("id").asText(), fila)).as("una persona, una fila").isNull());
        return filas;
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
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo), StandardCharsets.UTF_8));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

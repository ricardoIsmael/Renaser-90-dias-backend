package com.renaser.os.onboarding.infrastructure.adapter.in.rest.pagos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De punta a punta contra Postgres, Redis y la cadena HTTP completa (spec 032): Administración arma el cronograma de una persona, la persona lo ve en «Mi ingreso» y lo confirma,
 * Administración marca los pagos y ajusta lo pendiente, y la confirmación se vuelve a pedir solo cuando cambian las fechas o los montos. Solo ADMIN y ALCHEMIST operan los pagos;
 * la persona ve lo suyo y nada más, también entre las tres formaciones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CronogramaDePagosIT {

    private static final String MIO = "/api/v1/me/ingreso";
    private static final String ADMIN = "/api/v1/admin/pagos/personas/";

    @LocalServerPort private int puerto;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SessionRepository<? extends Session> sesiones;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();

    @AfterEach
    void limpiar() {
        tokens.forEach(sesiones::deleteById);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.cuotas_de_pago WHERE usuario_id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.estado_onboarding WHERE usuario_id = ?", id));
        usuarios.reversed().forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    // ------------------------------------------------------------------------------------------ el recorrido

    @Test
    @DisplayName("del cronograma a la confirmación: asigna, la persona lo ve y confirma, se marca un pago, se cambia una cuota y la confirmación se vuelve a pedir")
    void delCronogramaALaConfirmacion() throws Exception {
        Cuenta ana = cuenta("ADMIN", null);
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");

        // 1. Sin nada anotado: Administración lo ve vacío y la persona no tiene nada que mostrar (la fila de Yo no se dibuja)
        JsonNode vacio = ok(pedir("GET", ADMIN + persona.id, ana.token, null));
        assertThat(vacio.get("payments").isNull()).isTrue();
        assertThat(vacio.get("personName").asText()).isEqualTo("Fixture APRENDIZ");
        JsonNode nada = ok(pedir("GET", MIO, persona.token, null));
        assertThat(nada.get("payments").isNull()).isTrue();
        assertThat(nada.get("steps")).isEmpty();

        // 2. Administración arma el cronograma: tres cuotas en dólares
        JsonNode armado = ok(pedir("PUT", ADMIN + persona.id, ana.token, cronograma("USD", "2026-12-15", "2026-10-15", "2026-11-15")));
        assertThat(armado.get("payments").get("mode").asText()).isEqualTo("SCHEDULE");
        assertThat(armado.get("payments").get("currency").asText()).isEqualTo("USD");
        assertThat(armado.get("payments").get("confirmation").get("state").asText()).isEqualTo("NOT_YET");
        List<String> ids = idsDeLasCuotas(armado);
        assertThat(armado.get("payments").get("installments")).extracting(c -> c.get("dueDate").asText())
                .containsExactly("2026-10-15", "2026-11-15", "2026-12-15");
        assertThat(armado.get("payments").get("installments").get(0).get("amount").decimalValue()).isEqualByComparingTo("1000");

        // 3. La persona ve su cronograma, con los pasos de la maqueta; el nombre de quien marca es cosa de Administración
        JsonNode suyo = ok(pedir("GET", MIO, persona.token, null));
        assertThat(suyo.get("steps")).extracting(p -> p.get("key").asText() + ":" + p.get("done").asBoolean()).containsExactly("AGREEMENT:false", "PAYMENTS:false");
        assertThat(suyo.get("payments").get("installments")).hasSize(3);
        assertThat(suyo.get("payments").get("installments").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(suyo.get("payments").get("installments").get(0).get("statusChangedByName").isNull()).isTrue();

        // 4. Confirmar: sin la casilla o sin el nombre, no; con las dos, sí
        assertThat(pedir("POST", MIO + "/cronograma/confirmacion", persona.token, Map.of("name", "Beto", "reviewed", false)).statusCode()).isEqualTo(400);
        assertThat(pedir("POST", MIO + "/cronograma/confirmacion", persona.token, Map.of("name", "  ", "reviewed", true)).statusCode()).isEqualTo(400);
        JsonNode confirmado = ok(pedir("POST", MIO + "/cronograma/confirmacion", persona.token, Map.of("name", "Beto Aprendiz", "reviewed", true)));
        assertThat(confirmado.get("payments").get("confirmation").get("state").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmado.get("payments").get("confirmation").get("name").asText()).isEqualTo("Beto Aprendiz");
        assertThat(confirmado.get("payments").get("confirmation").get("confirmedAt").isNull()).isFalse();

        // 5. Administración marca la primera cuota pagada: la persona la ve pagada y su confirmación sigue vigente
        JsonNode pagada = ok(pedir("PUT", ADMIN + persona.id + "/cuotas/" + ids.get(0) + "/estado", ana.token, Map.of("status", "PAID")));
        assertThat(pagada.get("payments").get("installments").get(0).get("status").asText()).isEqualTo("PAID");
        assertThat(pagada.get("payments").get("installments").get(0).get("statusChangedByName").asText()).isEqualTo("Fixture ADMIN");
        JsonNode despuesDelPago = ok(pedir("GET", MIO, persona.token, null));
        assertThat(despuesDelPago.get("payments").get("installments").get(0).get("status").asText()).isEqualTo("PAID");
        assertThat(despuesDelPago.get("payments").get("confirmation").get("state").asText()).isEqualTo("CONFIRMED");

        // 6. Lo pagado no se toca: ni se edita, ni se quita, ni se reemplaza el cronograma
        assertThat(pedir("PUT", ADMIN + persona.id + "/cuotas/" + ids.get(0), ana.token, cuota("2026-10-20", 900)).statusCode()).isEqualTo(409);
        assertThat(pedir("DELETE", ADMIN + persona.id + "/cuotas/" + ids.get(0), ana.token, null).statusCode()).isEqualTo(409);
        assertThat(pedir("PUT", ADMIN + persona.id, ana.token, Map.of("mode", "NONE")).statusCode()).isEqualTo(409);

        // 7. Administración cambia el monto de una pendiente: la confirmación se vuelve a pedir
        JsonNode editada = ok(pedir("PUT", ADMIN + persona.id + "/cuotas/" + ids.get(1), ana.token, cuota("2026-11-20", 1200)));
        assertThat(editada.get("payments").get("installments").get(1).get("dueDate").asText()).isEqualTo("2026-11-20");
        JsonNode desactualizada = ok(pedir("GET", MIO, persona.token, null));
        assertThat(desactualizada.get("payments").get("confirmation").get("state").asText()).isEqualTo("OUTDATED");
        assertThat(desactualizada.get("payments").get("confirmation").get("name").asText()).isEqualTo("Beto Aprendiz");
        JsonNode otraVez = ok(pedir("POST", MIO + "/cronograma/confirmacion", persona.token, Map.of("name", "Beto Aprendiz Quispe", "reviewed", true)));
        assertThat(otraVez.get("payments").get("confirmation").get("state").asText()).isEqualTo("CONFIRMED");

        // 8. Una cuota más, y la última no se quita
        JsonNode conOtra = ok(pedir("POST", ADMIN + persona.id + "/cuotas", ana.token, cuota("2027-01-15", 1000)));
        assertThat(conOtra.get("payments").get("installments")).hasSize(4);
        assertThat(ok(pedir("DELETE", ADMIN + persona.id + "/cuotas/" + idsDeLasCuotas(conOtra).get(3), ana.token, null)).get("payments").get("installments")).hasSize(3);

        // 9. Devolver la cuota a «Pendiente» (un error al marcar): queda quién y cuándo
        JsonNode devuelta = ok(pedir("PUT", ADMIN + persona.id + "/cuotas/" + ids.get(0) + "/estado", ana.token, Map.of("status", "PENDING")));
        assertThat(devuelta.get("payments").get("installments").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(devuelta.get("payments").get("installments").get(0).get("statusChangedByName").asText()).isEqualTo("Fixture ADMIN");

        // 10. Nada bloquea a la persona: sigue entrando y leyendo lo suyo sin importar lo que pague o confirme (ver Cuenta activa: sesión viva y 200)
        assertThat(pedir("GET", MIO, persona.token, null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("«Sin cronograma»: la persona ve que pagó completo, sin cuotas; no hay nada que confirmar; y pasa a «con cronograma» si hace falta")
    void sinCronograma() throws Exception {
        Cuenta ana = cuenta("ADMIN", null);
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");

        JsonNode anotado = ok(pedir("PUT", ADMIN + persona.id, ana.token, Map.of("mode", "NONE")));
        assertThat(anotado.get("payments").get("mode").asText()).isEqualTo("NONE");
        assertThat(anotado.get("payments").get("installments")).isEmpty();
        assertThat(anotado.get("payments").get("confirmation").isNull()).isTrue();

        JsonNode suyo = ok(pedir("GET", MIO, persona.token, null));
        assertThat(suyo.get("payments").get("mode").asText()).isEqualTo("NONE");
        assertThat(suyo.get("steps")).extracting(p -> p.get("key").asText() + ":" + p.get("done").asBoolean()).containsExactly("AGREEMENT:false", "PAYMENTS:true");
        assertThat(pedir("POST", MIO + "/cronograma/confirmacion", persona.token, Map.of("name", "Beto", "reviewed", true)).statusCode()).isEqualTo(409);
        assertThat(pedir("POST", ADMIN + persona.id + "/cuotas", ana.token, cuota("2026-12-01", 10)).statusCode()).as("sin cronograma no se agregan cuotas").isEqualTo(409);

        JsonNode ahoraConCronograma = ok(pedir("PUT", ADMIN + persona.id, ana.token, cronograma("PEN", "2026-12-15")));
        assertThat(ahoraConCronograma.get("payments").get("currency").asText()).isEqualTo("PEN");
    }

    @Test
    @DisplayName("un cronograma inválido es 400 y no cambia nada: sin moneda, sin cuotas, monto cero, tres decimales, una fecha de otro siglo")
    void cronogramaInvalido() throws Exception {
        Cuenta ana = cuenta("ADMIN", null);
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");

        List<Map<String, Object>> invalidos = List.of(
                Map.of("mode", "SCHEDULE", "installments", List.of(cuota("2026-11-15", 10))),
                Map.of("mode", "SCHEDULE", "currency", "EUR", "installments", List.of(cuota("2026-11-15", 10))),
                Map.of("mode", "SCHEDULE", "currency", "PEN", "installments", List.of()),
                Map.of("mode", "SCHEDULE", "currency", "PEN", "installments", List.of(cuota("2026-11-15", 0))),
                Map.of("mode", "SCHEDULE", "currency", "PEN", "installments", List.of(Map.of("dueDate", "2026-11-15", "amount", 10.123))),
                Map.of("mode", "SCHEDULE", "currency", "PEN", "installments", List.of(cuota("2999-11-15", 10))),
                Map.of("mode", "OTRO"));
        for (Map<String, Object> invalido : invalidos) {
            assertThat(pedir("PUT", ADMIN + persona.id, ana.token, invalido).statusCode()).as("%s", invalido).isEqualTo(400);
        }
        assertThat(ok(pedir("GET", ADMIN + persona.id, ana.token, null)).get("payments").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.cuotas_de_pago WHERE usuario_id = ?", Integer.class, persona.id)).isZero();
    }

    // ------------------------------------------------------------------------------------------ quién puede

    @Test
    @DisplayName("Alquimista opera los pagos igual que Administración")
    void alquimistaOpera() throws Exception {
        Cuenta alquimista = cuenta("ALQUIMISTA", null);
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");

        JsonNode armado = ok(pedir("PUT", ADMIN + persona.id, alquimista.token, cronograma("PEN", "2026-11-15", "2026-12-15")));
        JsonNode pagada = ok(pedir("PUT", ADMIN + persona.id + "/cuotas/" + idsDeLasCuotas(armado).get(0) + "/estado", alquimista.token, Map.of("status", "PAID")));

        assertThat(pagada.get("payments").get("installments").get(0).get("status").asText()).isEqualTo("PAID");
        assertThat(pagada.get("payments").get("installments").get(0).get("statusChangedByName").asText()).isEqualTo("Fixture ALQUIMISTA");
    }

    @Test
    @DisplayName("mentor, guía, líder, Soporte TI y aprendiz no ven ni tocan los pagos de nadie (403) y no cambian nada; sin sesión, 401")
    void soloAdministracionYAlquimista() throws Exception {
        Cuenta ana = cuenta("ADMIN", null);
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");
        JsonNode armado = ok(pedir("PUT", ADMIN + persona.id, ana.token, cronograma("PEN", "2026-11-15", "2026-12-15")));
        String cuota = idsDeLasCuotas(armado).get(0);

        for (String rol : List.of("MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES", "SOPORTE_TI", "APRENDIZ")) {
            Cuenta intruso = cuenta(rol, "ESCUELA");
            assertThat(pedir("GET", ADMIN + persona.id, intruso.token, null).statusCode()).as("%s lee", rol).isEqualTo(403);
            assertThat(pedir("PUT", ADMIN + persona.id, intruso.token, Map.of("mode", "NONE")).statusCode()).as("%s asigna", rol).isEqualTo(403);
            assertThat(pedir("POST", ADMIN + persona.id + "/cuotas", intruso.token, cuota("2027-01-15", 10)).statusCode()).as("%s agrega", rol).isEqualTo(403);
            assertThat(pedir("PUT", ADMIN + persona.id + "/cuotas/" + cuota, intruso.token, cuota("2027-01-15", 10)).statusCode()).as("%s edita", rol).isEqualTo(403);
            assertThat(pedir("DELETE", ADMIN + persona.id + "/cuotas/" + cuota, intruso.token, null).statusCode()).as("%s quita", rol).isEqualTo(403);
            assertThat(pedir("PUT", ADMIN + persona.id + "/cuotas/" + cuota + "/estado", intruso.token, Map.of("status", "PAID")).statusCode()).as("%s marca", rol).isEqualTo(403);
        }
        assertThat(pedir("GET", ADMIN + persona.id, null, null).statusCode()).isEqualTo(401);
        assertThat(pedir("GET", MIO, null, null).statusCode()).isEqualTo(401);

        JsonNode igual = ok(pedir("GET", ADMIN + persona.id, ana.token, null));
        assertThat(igual.get("payments").get("installments")).hasSize(2);
        assertThat(igual.get("payments").get("installments").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(igual.get("payments").get("mode").asText()).isEqualTo("SCHEDULE");
    }

    @Test
    @DisplayName("una cuenta suspendida no ve «Mi ingreso» (403), ni siquiera la de Administración opera suspendida")
    void cuentaSuspendida() throws Exception {
        Cuenta ana = cuenta("ADMIN", null);
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");
        ok(pedir("PUT", ADMIN + persona.id, ana.token, cronograma("PEN", "2026-11-15")));

        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", persona.id);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", ana.id);

        assertThat(pedir("GET", MIO, persona.token, null).statusCode()).isEqualTo(403);
        assertThat(pedir("GET", ADMIN + persona.id, ana.token, null).statusCode()).isEqualTo(403);
    }

    // ------------------------------------------------------------------------------------------ aislamiento

    @Test
    @DisplayName("aislamiento: cada persona de Escuela, Diplomado y Certificación ve solo lo suyo; Administración y Alquimista ven las tres; ninguna cuota se toca por la ruta de otra persona")
    void aislamientoEntreFormaciones() throws Exception {
        Cuenta ana = cuenta("ADMIN", null);
        Cuenta alquimista = cuenta("ALQUIMISTA", null);
        Cuenta escuela = cuenta("APRENDIZ", "ESCUELA");
        Cuenta diplomado = cuenta("APRENDIZ", "DIPLOMADO");
        Cuenta certificacion = cuenta("APRENDIZ", "CERTIFICACION");
        Map<Cuenta, Integer> montos = Map.of(escuela, 100, diplomado, 200, certificacion, 300);
        Map<Cuenta, List<String>> cuotas = new java.util.HashMap<>();
        for (var entrada : montos.entrySet()) {
            JsonNode armado = ok(pedir("PUT", ADMIN + entrada.getKey().id, ana.token,
                    Map.of("mode", "SCHEDULE", "currency", "PEN", "installments", List.of(cuota("2026-11-15", entrada.getValue())))));
            cuotas.put(entrada.getKey(), idsDeLasCuotas(armado));
        }

        for (var entrada : montos.entrySet()) {
            JsonNode suyo = ok(pedir("GET", MIO, entrada.getKey().token, null));
            assertThat(suyo.get("payments").get("installments")).as("lo suyo").hasSize(1);
            assertThat(suyo.get("payments").get("installments").get(0).get("amount").decimalValue()).isEqualByComparingTo(String.valueOf(entrada.getValue()));
            assertThat(suyo.get("payments").get("installments").get(0).get("id").asText()).isEqualTo(cuotas.get(entrada.getKey()).get(0));
            for (String lector : List.of(ana.token, alquimista.token)) {
                JsonNode lectura = ok(pedir("GET", ADMIN + entrada.getKey().id, lector, null));
                assertThat(lectura.get("payments").get("installments").get(0).get("amount").decimalValue()).isEqualByComparingTo(String.valueOf(entrada.getValue()));
            }
            // la persona no puede usar las rutas de Administración para leer a las otras dos
            for (Cuenta otra : montos.keySet()) {
                assertThat(pedir("GET", ADMIN + otra.id, entrada.getKey().token, null).statusCode()).isEqualTo(403);
            }
        }

        // la cuota de una persona no se toca por la ruta de otra: es 404 y la cuota ajena queda como estaba
        assertThat(pedir("PUT", ADMIN + escuela.id + "/cuotas/" + cuotas.get(diplomado).get(0) + "/estado", ana.token, Map.of("status", "PAID")).statusCode()).isEqualTo(404);
        assertThat(pedir("PUT", ADMIN + escuela.id + "/cuotas/" + cuotas.get(diplomado).get(0), ana.token, cuota("2026-12-01", 5)).statusCode()).isEqualTo(404);
        JsonNode delDiplomado = ok(pedir("GET", ADMIN + diplomado.id, ana.token, null));
        assertThat(delDiplomado.get("payments").get("installments").get(0).get("status").asText()).isEqualTo("PENDING");
        assertThat(delDiplomado.get("payments").get("installments").get(0).get("amount").decimalValue()).isEqualByComparingTo("200");

        // y el personal que acompaña a una de las formaciones no ve ninguno
        Cuenta mentor = cuenta("MENTOR", "ESCUELA");
        for (Cuenta persona : montos.keySet()) {
            assertThat(pedir("GET", ADMIN + persona.id, mentor.token, null).statusCode()).isEqualTo(403);
        }
    }

    // ------------------------------------------------------------------------------------------ la base

    @Test
    @DisplayName("la base cuida lo suyo: una pagada dice cuándo, un cronograma lleva moneda, la confirmación va completa y el monto es positivo")
    void laBaseCuidaLoSuyo() throws Exception {
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");
        jdbc.update("INSERT INTO renaser.estado_onboarding (usuario_id, creado_en, actualizado_en) VALUES (?, now(), now())", persona.id);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE renaser.estado_onboarding SET pago_modalidad = 'CON_CRONOGRAMA' WHERE usuario_id = ?", persona.id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE renaser.estado_onboarding SET pago_modalidad = 'SIN_CRONOGRAMA', pago_moneda = 'PEN' WHERE usuario_id = ?", persona.id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE renaser.estado_onboarding SET pago_modalidad = 'CON_CRONOGRAMA', pago_moneda = 'PEN', cronograma_confirmado_en = now() WHERE usuario_id = ?", persona.id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO renaser.cuotas_de_pago (id, usuario_id, vence_el, monto, estado) VALUES (?, ?, DATE '2026-11-15', 0, 'PENDIENTE')", UUID.randomUUID(), persona.id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO renaser.cuotas_de_pago (id, usuario_id, vence_el, monto, estado) VALUES (?, ?, DATE '2026-11-15', 10, 'PAGADA')", UUID.randomUUID(), persona.id));
    }

    @Test
    @DisplayName("el panel de onboarding cuenta como iniciados solo a quienes de verdad empezaron: una fila que nace por un pago anotado no cuenta")
    void unaFilaPorUnPagoNoEsUnaPersonaIniciada() throws Exception {
        Cuenta ana = cuenta("ADMIN", null);
        Cuenta persona = cuenta("APRENDIZ", "ESCUELA");
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM renaser.estado_onboarding WHERE iniciado_en IS NOT NULL", Integer.class);

        ok(pedir("PUT", ADMIN + persona.id, ana.token, Map.of("mode", "NONE")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.estado_onboarding WHERE usuario_id = ?", Integer.class, persona.id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.estado_onboarding WHERE iniciado_en IS NOT NULL", Integer.class)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT iniciado_en FROM renaser.estado_onboarding WHERE usuario_id = ?", java.sql.Timestamp.class, persona.id)).isNull();
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
        String token = sesionDe(id);
        tokens.add(token);
        return new Cuenta(id, token);
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

    private static Map<String, Object> cuota(String fecha, int monto) {
        return Map.of("dueDate", fecha, "amount", monto);
    }

    private static Map<String, Object> cronograma(String moneda, String... fechas) {
        List<Map<String, Object>> cuotas = new ArrayList<>();
        for (String fecha : fechas) {
            cuotas.add(cuota(fecha, 1000));
        }
        return Map.of("mode", "SCHEDULE", "currency", moneda, "installments", cuotas);
    }

    private static List<String> idsDeLasCuotas(JsonNode respuesta) {
        List<String> ids = new ArrayList<>();
        respuesta.get("payments").get("installments").forEach(c -> ids.add(c.get("id").asText()));
        return ids;
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

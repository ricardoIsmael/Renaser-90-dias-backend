package com.renaser.os.leadership;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
 * «Descargar semana» de punta a punta sobre el Tomcat real, con la sesión de verdad y todo en Postgres (spec 056, FR-032, FR-035 a FR-041, FR-047,
 * SC-006 y SC-007): los tres documentos, su reutilización, el enlace de 10 minutos, el registro de cada pedido y quién puede pedir qué. El
 * almacenamiento es el de memoria del escenario común ({@link AlmacenamientoDeLasPruebasDelLider}), que anota cada subida y cada enlace: el de las
 * pruebas (noop) no guarda nada que después se pueda leer.
 */
class DocumentoSemanalIT extends EscenarioDelLiderIT {

    @Autowired
    AlmacenamientoDeLasPruebasDelLider.AlmacenamientoDelLiderEnMemoria almacen;

    @AfterEach
    void limpiarLosDocumentos() {
        List<UUID> personas = List.of(ana, beto, mentora, mentorConPrograma, mentorSinPrograma, lider, admin);
        personas.forEach(persona -> {
            jdbc.update("DELETE FROM renaser.descargas_de_documentos WHERE documento_id IN "
                    + "(SELECT id FROM renaser.documentos_semanales WHERE persona_id = ?)", persona);
            jdbc.update("DELETE FROM renaser.documentos_semanales WHERE persona_id = ?", persona);
        });
        jdbc.update("DELETE FROM renaser.notas_del_mentor WHERE aprendiz_id = ?", ana);
        jdbc.update("DELETE FROM renaser.observaciones_mentor WHERE mentor_id = ?", mentora);
        almacen.vaciar();
    }

    // ── pedidos y lecturas ──────────────────────────────────────────────────

    private HttpResponse<String> deLaPersona(UUID persona, String tipo, String semanaHasta, UUID quien) throws Exception {
        return post("/api/v1/leadership/people/" + persona + "/documento", cuerpoDe("tipo", tipo, "semanaHasta", semanaHasta), sesionDe(quien));
    }

    private HttpResponse<String> deLaPersona(UUID persona, UUID quien) throws Exception {
        return deLaPersona(persona, "APRENDIZ", null, quien);
    }

    private HttpResponse<String> deUnMentor(UUID mentor, UUID quien) throws Exception {
        return post("/api/v1/leadership/mentors/" + mentor + "/documento", Map.of(), sesionDe(quien));
    }

    private HttpResponse<String> delMes(String mes, UUID quien) throws Exception {
        return post("/api/v1/leadership/report/documento", Map.of("month", mes), sesionDe(quien));
    }

    private HttpResponse<String> post(String ruta, Object cuerpo, String sesion) throws Exception {
        return pedir("POST", ruta, sesion, cuerpo);
    }

    private static Map<String, Object> cuerpoDe(String clave1, String valor1, String clave2, String valor2) {
        Map<String, Object> cuerpo = new java.util.HashMap<>();
        cuerpo.put(clave1, valor1);
        if (valor2 != null) {
            cuerpo.put(clave2, valor2);
        }
        return cuerpo;
    }

    /** La ruta del archivo en el almacenamiento, sacada del enlace que se entregó. */
    private static String rutaDe(HttpResponse<String> respuesta, com.fasterxml.jackson.databind.ObjectMapper json) throws Exception {
        return URI.create(json.readTree(respuesta.body()).get("url").asText()).getPath().substring(1);
    }

    private String html(HttpResponse<String> respuesta) throws Exception {
        return new String(almacen.objetos.get(rutaDe(respuesta, json)), StandardCharsets.UTF_8);
    }

    private long documentos(UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.documentos_semanales WHERE persona_id = ?", Long.class, persona);
    }

    private long descargas(UUID actor) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.descargas_de_documentos WHERE actor_id = ?", Long.class, actor);
    }

    /** Una semana con algo de cumplimiento: dos hábitos el martes, uno el lunes y el semáforo de cuatro días sin nada cumplido. */
    private void semanaDeAna() {
        habitoDelDia(ana, "2026-11-17", "Caminar", "CUERPO", "COMPLETADO");
        habitoDelDia(ana, "2026-11-17", "Meditar", "CONSCIENCIA", "FALLIDO");
        habitoDelDia(ana, "2026-11-16", "Leer", "MENTE", "COMPLETADO");
        for (String dia : List.of("2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17")) {
            diaDelSemaforo(ana, dia, 3, 0);
        }
    }

    // ── el documento del aprendiz ───────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-035 FR-039 SC-006: el líder pide la semana de una aprendiz y recibe el enlace de 10 minutos, la semana de sábado a viernes y el documento guardado con sus datos")
    void elLiderGeneraElDocumentoDeUnaAprendiz() throws Exception {
        semanaDeAna();

        long empezo = System.nanoTime();
        HttpResponse<String> respuesta = deLaPersona(ana, lider);
        Duration tardo = Duration.ofNanos(System.nanoTime() - empezo);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(tardo).as("SC-006: el enlace en menos de 10 segundos").isLessThan(Duration.ofSeconds(10));
        JsonNode cuerpo = cuerpo(respuesta);
        assertThat(cuerpo.get("reutilizado").asBoolean()).isFalse();
        assertThat(cuerpo.get("semana").get("desde").asText()).isEqualTo("2026-11-14");
        assertThat(cuerpo.get("semana").get("hasta").asText()).isEqualTo("2026-11-20");
        assertThat(cuerpo.get("url").asText()).startsWith("https://almacen.test/reportes/" + ana + "/2026-11-14-APRENDIZ-").endsWith(".html?vence=600");
        assertThat(Instant.parse(cuerpo.get("venceEn").asText())).as("vence a los 10 minutos del pedido").isEqualTo(AHORA.plusSeconds(600));
        String ruta = rutaDe(respuesta, json);
        assertThat(almacen.tipos.get(ruta)).isEqualTo("text/html; charset=utf-8");
        String formacion = jdbc.queryForObject("SELECT nombre FROM renaser.formaciones WHERE codigo = 'ESCUELA'", String.class);
        Integer duracion = jdbc.queryForObject("SELECT duracion_dias FROM renaser.participantes_programa WHERE usuario_id = ?", Integer.class, ana);
        assertThat(html(respuesta)).contains("Ana Pérez", formacion, "Luisa Rojas", "Fénix", "Caminar", "Meditar", "Leer", "Cuerpo", "Emociones", "Mente")
                .containsPattern("Día \\d+ de " + duracion).contains("14 de noviembre de 2026", "20 de noviembre de 2026")
                .contains("Foco para la próxima semana").doesNotContain("null");
        assertThat(documentos(ana)).isEqualTo(1);
        Map<String, Object> descarga = jdbc.queryForMap("SELECT actor_id, reutilizado, pedido_en FROM renaser.descargas_de_documentos");
        assertThat(descarga.get("actor_id")).isEqualTo(lider);
        assertThat(descarga.get("reutilizado")).isEqualTo(false);
    }

    @Test
    @DisplayName("spec 056 FR-039 SC-006: pedirla otra vez sin cambios entrega el mismo documento, sin generarlo ni subirlo de nuevo, con un enlace nuevo y su descarga registrada")
    void sinCambiosSeReutiliza() throws Exception {
        semanaDeAna();
        HttpResponse<String> primera = deLaPersona(ana, lider);

        long empezo = System.nanoTime();
        HttpResponse<String> segunda = deLaPersona(ana, lider);
        Duration tardo = Duration.ofNanos(System.nanoTime() - empezo);

        assertThat(segunda.statusCode()).as(segunda.body()).isEqualTo(200);
        assertThat(tardo).as("SC-006: la segunda vez en menos de 3 segundos").isLessThan(Duration.ofSeconds(3));
        assertThat(cuerpo(segunda).get("reutilizado").asBoolean()).isTrue();
        assertThat(rutaDe(segunda, json)).isEqualTo(rutaDe(primera, json));
        assertThat(almacen.subidas).as("se subió una sola vez").hasSize(1);
        assertThat(documentos(ana)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT reutilizado FROM renaser.descargas_de_documentos ORDER BY pedido_en, reutilizado", Boolean.class))
                .containsExactly(false, true);
    }

    @Test
    @DisplayName("spec 056 FR-039: si los datos cambian sale otro documento y el anterior se queda guardado: se guardan para siempre")
    void siCambianLosDatosSaleOtro() throws Exception {
        semanaDeAna();
        HttpResponse<String> primera = deLaPersona(ana, lider);
        habitoDelDia(ana, "2026-11-17", "Ducha fría", "CUERPO", "COMPLETADO");

        HttpResponse<String> segunda = deLaPersona(ana, lider);

        assertThat(cuerpo(segunda).get("reutilizado").asBoolean()).isFalse();
        assertThat(rutaDe(segunda, json)).isNotEqualTo(rutaDe(primera, json));
        assertThat(almacen.objetos).containsKeys(rutaDe(primera, json), rutaDe(segunda, json));
        assertThat(documentos(ana)).isEqualTo(2);
        assertThat(html(segunda)).contains("Ducha fría");
    }

    @Test
    @DisplayName("spec 056 FR-039 FR-040: dos pedidos a la vez del mismo documento dejan un solo documento y dos descargas, y los dos reciben el enlace; solo uno lo generó")
    void dosPedidosALaVez() throws Exception {
        semanaDeAna();
        String sesionUno = sesionDe(lider);
        String sesionDos = sesionDe(admin);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpResponse<String>> uno = () -> {
                salida.await();
                return post("/api/v1/leadership/people/" + ana + "/documento", Map.of("tipo", "APRENDIZ"), sesionUno);
            };
            Callable<HttpResponse<String>> dos = () -> {
                salida.await();
                return post("/api/v1/leadership/people/" + ana + "/documento", Map.of("tipo", "APRENDIZ"), sesionDos);
            };
            Future<HttpResponse<String>> a = hilos.submit(uno);
            Future<HttpResponse<String>> b = hilos.submit(dos);
            salida.countDown();
            List<HttpResponse<String>> respuestas = List.of(a.get(), b.get());

            assertThat(respuestas).extracting(HttpResponse::statusCode).containsExactly(200, 200);
            assertThat(respuestas.stream().filter(r -> !leer(r).get("reutilizado").asBoolean())).hasSize(1);
            assertThat(rutaDe(respuestas.get(0), json)).isEqualTo(rutaDe(respuestas.get(1), json));
        } finally {
            hilos.shutdownNow();
        }
        assertThat(documentos(ana)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.descargas_de_documentos", Long.class)).isEqualTo(2L);
        assertThat(almacen.objetos).hasSize(1);
    }

    private JsonNode leer(HttpResponse<String> respuesta) {
        try {
            return cuerpo(respuesta);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("spec 056 FR-035: con semanaHasta pide otra semana: la de sábado 7 a viernes 13 de noviembre")
    void otraSemana() throws Exception {
        HttpResponse<String> respuesta = deLaPersona(ana, "APRENDIZ", "2026-11-13", lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(cuerpo(respuesta).get("semana").get("desde").asText()).isEqualTo("2026-11-07");
        assertThat(cuerpo(respuesta).get("semana").get("hasta").asText()).isEqualTo("2026-11-13");
        assertThat(html(respuesta)).contains("7 de noviembre de 2026", "13 de noviembre de 2026");
    }

    @Test
    @DisplayName("spec 056 FR-038: una semana en que no hubo nada programado dice «Sin actividad esta semana» en vez de tablas vacías")
    void laSemanaSinActividad() throws Exception {
        HttpResponse<String> respuesta = deLaPersona(ana, "APRENDIZ", "2026-10-30", lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(html(respuesta)).contains("Sin actividad esta semana").doesNotContain("<table");
    }

    @Test
    @DisplayName("spec 056 FR-035: una fecha que no es viernes es 400 SEMANA_INVALIDA; antes del Día 1 o una semana futura, 400 FUERA_DEL_PROGRAMA; y no queda nada")
    void fechasInvalidas() throws Exception {
        HttpResponse<String> noViernes = deLaPersona(ana, "APRENDIZ", "2026-11-17", lider);

        assertThat(noViernes.statusCode()).isEqualTo(400);
        assertThat(codigo(noViernes)).isEqualTo("SEMANA_INVALIDA");
        for (String viernes : new String[] {"2026-10-02", "2026-11-27"}) {
            HttpResponse<String> respuesta = deLaPersona(ana, "APRENDIZ", viernes, lider);

            assertThat(respuesta.statusCode()).as(viernes).isEqualTo(400);
            assertThat(codigo(respuesta)).as(viernes).isEqualTo("FUERA_DEL_PROGRAMA");
        }
        assertThat(almacen.subidas).isEmpty();
        assertThat(documentos(ana)).isZero();
        assertThat(descargas(lider)).isZero();
    }

    @Test
    @DisplayName("spec 056 FR-035: un pedido sin tipo es 400 y un tipo que no existe también")
    void elPedidoSinTipo() throws Exception {
        assertThat(post("/api/v1/leadership/people/" + ana + "/documento", Map.of(), sesionDe(lider)).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/leadership/people/" + ana + "/documento", Map.of("tipo", "MES"), sesionDe(lider)).statusCode()).isEqualTo(400);
    }

    // ── quién puede pedirlo ─────────────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-041: el aprendiz lo piden el líder, Administración y el mentor que lo acompaña hoy; quien no tiene grupo también lo tiene para el liderazgo")
    void quienPuedePedirElDeUnAprendiz() throws Exception {
        for (UUID quien : List.of(lider, admin, mentora)) {
            HttpResponse<String> respuesta = deLaPersona(ana, quien);

            assertThat(respuesta.statusCode()).as("actor " + quien + ": " + respuesta.body()).isEqualTo(200);
        }
        assertThat(deLaPersona(beto, lider).statusCode()).as("sin grupo, para el liderazgo").isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT actor_id FROM renaser.descargas_de_documentos WHERE documento_id IN "
                + "(SELECT id FROM renaser.documentos_semanales WHERE persona_id = ?) ORDER BY pedido_en, reutilizado", UUID.class, ana))
                .containsExactlyInAnyOrder(lider, admin, mentora);
    }

    @Test
    @DisplayName("spec 056 FR-041 FR-047: el mentor que no acompaña al aprendiz recibe 403 NO_ACOMPANA y no se genera ni se registra nada")
    void elMentorQueNoLoAcompana() throws Exception {
        for (UUID aprendiz : List.of(ana, beto)) {
            HttpResponse<String> deOtroMentor = deLaPersona(aprendiz, mentorConPrograma);

            assertThat(deOtroMentor.statusCode()).as("Marco no acompaña a " + aprendiz).isEqualTo(403);
            assertThat(codigo(deOtroMentor)).isEqualTo("NO_ACOMPANA");
        }
        assertThat(codigo(deLaPersona(beto, mentora))).as("Luisa acompaña a Ana, no a Beto").isEqualTo("NO_ACOMPANA");
        assertThat(almacen.subidas).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.documentos_semanales", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.descargas_de_documentos", Long.class)).isZero();
    }

    @Test
    @DisplayName("spec 056 FR-041: un aprendiz no pide ningún documento, ni el suyo; una cuenta suspendida del liderazgo o del mentor, tampoco")
    void losQueNoPuedenPedirNada() throws Exception {
        assertThat(deLaPersona(ana, ana).statusCode()).as("la propia aprendiz").isEqualTo(403);
        assertThat(deLaPersona(ana, beto).statusCode()).as("otro aprendiz").isEqualTo(403);
        assertThat(deUnMentor(mentora, ana).statusCode()).isEqualTo(403);
        assertThat(delMes("2026-11", ana).statusCode()).isEqualTo(403);
        String sesionDelLider = sesionDe(lider);
        String sesionDeLaMentora = sesionDe(mentora);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id IN (?, ?)", lider, mentora);

        assertThat(post("/api/v1/leadership/people/" + ana + "/documento", Map.of("tipo", "APRENDIZ"), sesionDelLider).statusCode())
                .as("líder suspendido con la sesión viva").isEqualTo(403);
        assertThat(post("/api/v1/leadership/people/" + ana + "/documento", Map.of("tipo", "APRENDIZ"), sesionDeLaMentora).statusCode())
                .as("mentora suspendida con la sesión viva").isEqualTo(403);
        assertThat(almacen.subidas).isEmpty();
    }

    @Test
    @DisplayName("spec 056 FR-041: una persona de otra formación o que no existe es 404 sin pista, como el resto de lecturas del líder (spec 022 FR-020); nada se genera")
    void deOtraFormacion() throws Exception {
        HttpResponse<String> delDiplomado = deLaPersona(dani, lider);
        HttpResponse<String> inexistente = deLaPersona(UUID.randomUUID(), lider);

        assertThat(delDiplomado.statusCode()).isEqualTo(404);
        assertThat(codigo(delDiplomado)).isNull();
        assertThat(inexistente.statusCode()).isEqualTo(404);
        assertThat(codigo(inexistente)).isNull();
        assertThat(almacen.subidas).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.documentos_semanales", Long.class)).isZero();
    }

    // ── el documento del mentor ─────────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-036: el documento del mentor trae su grupo, sus aprendices con el estado de la semana, las alertas y lo que el líder le dijo ese día")
    void elDocumentoDelMentor() throws Exception {
        semanaDeAna();
        jdbc.update("INSERT INTO renaser.observaciones_mentor (id, mentor_id, autor_id, tipo, texto, clave_operacion, creado_en) "
                + "VALUES (?, ?, ?, 'SUGERENCIA', ?, 'prueba-documento', TIMESTAMPTZ '2026-11-17 15:00:00+00')",
                UUID.randomUUID(), mentora, lider, "Escríbele a Ana esta semana.");

        HttpResponse<String> respuesta = deUnMentor(mentora, lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(cuerpo(respuesta).get("semana").get("desde").asText()).isEqualTo("2026-11-14");
        assertThat(cuerpo(respuesta).get("semana").get("hasta").asText()).isEqualTo("2026-11-20");
        assertThat(rutaDe(respuesta, json)).startsWith("reportes/" + mentora + "/2026-11-14-MENTOR-");
        assertThat(html(respuesta)).contains("Luisa Rojas", "Fénix", "Ana Pérez", "Líder Uno", "Escríbele a Ana esta semana.", "Sugerencia")
                .contains("4 días sin actividad").contains("Escribir a los aprendices en rojo").doesNotContain("null");
        assertThat(documentos(mentora)).isEqualTo(1);
    }

    @Test
    @DisplayName("spec 056 FR-036: un mentor sin grupo se dice en una línea; y el mismo documento por la ruta de persona con tipo MENTOR es el mismo, reutilizado")
    void elMentorSinGrupoYLaRutaDePersona() throws Exception {
        HttpResponse<String> sinGrupo = deUnMentor(mentorSinPrograma, lider);

        assertThat(sinGrupo.statusCode()).as(sinGrupo.body()).isEqualTo(200);
        assertThat(html(sinGrupo)).contains("Sin grupo asignado").doesNotContain("<table");
        deUnMentor(mentora, lider);
        HttpResponse<String> porPersona = deLaPersona(mentora, "MENTOR", null, lider);

        assertThat(porPersona.statusCode()).as(porPersona.body()).isEqualTo(200);
        assertThat(cuerpo(porPersona).get("reutilizado").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("spec 056 FR-036: si el mentor sigue su programa suma su propia semana; y la ruta de aprendiz le da solo esa semana. Si no la sigue, es 404 SIN_PROGRAMA")
    void suPropiaSemana() throws Exception {
        habitoDelDia(mentorConPrograma, "2026-11-17", "Leer", "CONSCIENCIA", "COMPLETADO");

        HttpResponse<String> delMentor = deUnMentor(mentorConPrograma, lider);
        HttpResponse<String> soloSuSemana = deLaPersona(mentorConPrograma, lider);
        HttpResponse<String> sinPrograma = deLaPersona(mentorSinPrograma, lider);

        assertThat(delMentor.statusCode()).as(delMentor.body()).isEqualTo(200);
        assertThat(html(delMentor)).contains("Su propia semana", "Marco Sin Grupo", "Leer");
        assertThat(soloSuSemana.statusCode()).as(soloSuSemana.body()).isEqualTo(200);
        assertThat(html(soloSuSemana)).contains("Marco Sin Grupo", "Leer", "No se mide en el semáforo");
        assertThat(sinPrograma.statusCode()).isEqualTo(404);
        assertThat(codigo(sinPrograma)).isEqualTo("SIN_PROGRAMA");
    }

    @Test
    @DisplayName("spec 056 FR-041: el documento de un mentor lo piden el líder y Administración; el propio mentor y un aprendiz no; un id que no es de un mentor es 404")
    void quienPuedePedirElDeUnMentor() throws Exception {
        assertThat(deUnMentor(mentora, lider).statusCode()).isEqualTo(200);
        assertThat(deUnMentor(mentora, admin).statusCode()).isEqualTo(200);
        assertThat(deUnMentor(mentora, mentora).statusCode()).as("ni el propio mentor").isEqualTo(403);
        assertThat(deUnMentor(mentora, mentorConPrograma).statusCode()).isEqualTo(403);
        assertThat(deLaPersona(mentora, "MENTOR", null, mentora).statusCode()).as("ni por la ruta de persona").isEqualTo(403);
        assertThat(deUnMentor(ana, lider).statusCode()).as("Ana es aprendiz: no hay un mentor con ese id").isEqualTo(404);
        assertThat(deUnMentor(UUID.randomUUID(), lider).statusCode()).isEqualTo(404);
    }

    // ── el reporte del mes ──────────────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-032 FR-039: el reporte del mes se entrega como documento de quien lo pidió, de noviembre completo, y pedirlo otra vez lo reutiliza")
    void elReporteDelMes() throws Exception {
        HttpResponse<String> respuesta = delMes("2026-11", lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(cuerpo(respuesta).get("semana").get("desde").asText()).isEqualTo("2026-11-01");
        assertThat(cuerpo(respuesta).get("semana").get("hasta").asText()).isEqualTo("2026-11-30");
        assertThat(rutaDe(respuesta, json)).startsWith("reportes/" + lider + "/2026-11-01-MES-");
        assertThat(html(respuesta)).contains("Reporte de noviembre de 2026").doesNotContain("null");
        assertThat(documentos(lider)).isEqualTo(1);
        assertThat(cuerpo(delMes("2026-11", lider)).get("reutilizado").asBoolean()).isTrue();
        assertThat(delMes("2026-11", admin).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("spec 056 FR-032: el mes tiene que ser AAAA-MM y haber empezado; el mentor y la aprendiz no piden el reporte")
    void elMesYQuienLoPide() throws Exception {
        assertThat(delMes("2026-13", lider).statusCode()).isEqualTo(400);
        assertThat(delMes("noviembre", lider).statusCode()).isEqualTo(400);
        assertThat(delMes("2027-01", lider).statusCode()).as("un mes que todavía no empezó").isEqualTo(400);
        assertThat(delMes("2026-11", mentora).statusCode()).isEqualTo(403);
        assertThat(delMes("2026-11", ana).statusCode()).isEqualTo(403);
        assertThat(almacen.subidas).isEmpty();
    }

    // ── lo que nunca lleva ──────────────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-037 SC-007: ningún documento —de la aprendiz, del mentor ni del mes— contiene la nota privada del mentor")
    void ningunDocumentoLlevaLaNotaPrivada() throws Exception {
        semanaDeAna();
        jdbc.update("INSERT INTO renaser.notas_del_mentor (id, aprendiz_id, autor_id, texto, dia_del_autor, creado_en) "
                + "VALUES (?, ?, ?, 'SECRETO-DE-LA-NOTA-PRIVADA', DATE '2026-11-17', TIMESTAMPTZ '2026-11-17 15:00:00+00')",
                UUID.randomUUID(), ana, mentora);

        List<HttpResponse<String>> respuestas = new ArrayList<>(List.of(deLaPersona(ana, lider), deLaPersona(ana, mentora), deUnMentor(mentora, lider),
                delMes("2026-11", lider)));

        assertThat(respuestas).extracting(HttpResponse::statusCode).containsOnly(200);
        assertThat(almacen.objetos).isNotEmpty();
        almacen.objetos.forEach((ruta, contenido) -> assertThat(new String(contenido, StandardCharsets.UTF_8))
                .as(ruta).doesNotContain("SECRETO-DE-LA-NOTA-PRIVADA").doesNotContain("nota privada"));
    }

    @Test
    @DisplayName("spec 056 FR-035: lo que una persona escribió y su nombre se escapan: un nombre con HTML no llega como HTML al documento guardado")
    void elHtmlDeUnaPersonaNoPasa() throws Exception {
        semanaDeAna();
        jdbc.update("UPDATE renaser.usuarios SET nombre_completo = ? WHERE id = ?", "Ana <script>alert(1)</script> \"Pérez\"", ana);

        HttpResponse<String> respuesta = deLaPersona(ana, lider);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        assertThat(html(respuesta)).doesNotContain("<script").contains("Ana &lt;script&gt;alert(1)&lt;/script&gt; &quot;Pérez&quot;");
    }
}

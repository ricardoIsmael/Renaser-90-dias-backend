package com.renaser.os.leadership;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El líder de mentores ve a todos los aprendices, estén o no en un grupo (spec 021), de punta a punta: Tomcat real, la
 * sesión de verdad, el filtro de permisos y Postgres con las migraciones (V124 incluida). Lo que solo se ve así: que el
 * registro de consulta se guarde con el grupo en NULL, que los 403 los dé el guard y no un mock, y que el cuerpo de
 * {@code /people/{p}/week} sea el de {@code /groups/{g}/people/{p}/week}.
 *
 * <p>Semilla: «Fénix» (en curso) con su mentora Luisa y su aprendiz Ana; Beto, Aprendiz sin grupo; una cuenta de prueba y
 * una suspendida; y los actores que no deben pasar: otro mentor, una guía, un aprendiz y Soporte TI. Los nombres llevan
 * un sufijo propio de la corrida para filtrar la lista, que es global.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LiderVeATodosIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();

    private String marca;
    private UUID lider;
    private UUID admin;
    private UUID mentora;
    private UUID otroMentor;
    private UUID guia;
    private UUID aprendizActor;
    private UUID soporteTi;
    private UUID ana;
    private UUID beto;
    private UUID prueba;
    private UUID suspendida;
    private UUID fenix;
    private UUID cohorte;

    @BeforeEach
    void sembrar() {
        marca = "LV" + UUID.randomUUID().toString().substring(0, 8);
        lider = usuario("LIDER_MENTORES", "Líder " + marca, "ACTIVO", false);
        admin = usuario("ADMIN", "Admin " + marca, "ACTIVO", false);
        mentora = usuario("MENTOR", "Luisa Rojas " + marca, "ACTIVO", false);
        otroMentor = usuario("MENTOR", "Otro Mentor " + marca, "ACTIVO", false);
        guia = usuario("GUIA_APRENDIZ", "Guía " + marca, "ACTIVO", false);
        aprendizActor = usuario("APRENDIZ", "Actor Aprendiz " + marca, "ACTIVO", false);
        soporteTi = usuario("SOPORTE_TI", "Soporte " + marca, "ACTIVO", false);
        ana = usuario("APRENDIZ", "Ana Pérez " + marca, "ACTIVO", false);
        beto = usuario("APRENDIZ", "Beto Álvarez " + marca, "ACTIVO", false);
        prueba = usuario("APRENDIZ", "Prueba " + marca, "ACTIVO", true);
        suspendida = usuario("APRENDIZ", "Suspendida " + marca, "SUSPENDIDO", false);
        List.of(ana, beto, prueba, suspendida).forEach(this::programa);
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, ?, CURRENT_DATE - 60)",
                cohorte, "Cohorte " + marca);
        fenix = grupo("Fénix " + marca, mentora);
        asignar(fenix, mentora, "MENTOR");
        asignar(fenix, ana, "APRENDIZ");
    }

    @AfterEach
    void limpiar() {
        grupos.forEach(id -> {
            jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id);
            jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id);
        });
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    // ── la lista ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el líder ve en la lista a la aprendiz con grupo y al aprendiz sin grupo; no la cuenta de prueba, la suspendida ni el correo")
    void laLista() throws Exception {
        HttpResponse<String> respuesta = pedir("/api/v1/leadership/people?q=" + marca + "&size=50", lider);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("total").asInt()).isEqualTo(2);
        JsonNode items = cuerpo.get("items");
        assertThat(items).hasSize(2);
        JsonNode deAna = items.get(0);
        assertThat(deAna.get("personId").asText()).isEqualTo(ana.toString());
        assertThat(deAna.get("nombre").asText()).isEqualTo("Ana Pérez " + marca);
        assertThat(deAna.get("grupoId").asText()).isEqualTo(fenix.toString());
        assertThat(deAna.get("grupoNombre").asText()).isEqualTo("Fénix " + marca);
        assertThat(deAna.get("mentorNombre").asText()).isEqualTo("Luisa Rojas " + marca);
        assertThat(deAna.get("diaPrograma").isNumber()).isTrue();
        JsonNode deBeto = items.get(1);
        assertThat(deBeto.get("personId").asText()).isEqualTo(beto.toString());
        assertThat(deBeto.get("grupoId").isNull()).isTrue();
        assertThat(deBeto.get("mentorNombre").isNull()).isTrue();
        assertThat(respuesta.body()).doesNotContain("@renaser.test").doesNotContain("email").doesNotContain("Prueba ")
                .doesNotContain("Suspendida ");
    }

    @Test
    @DisplayName("la búsqueda es sin tildes ni mayúsculas, y la página más allá del final viene vacía con el total")
    void busquedaYPaginas() throws Exception {
        JsonNode porNombre = obtener("/api/v1/leadership/people?q=" + marca.toLowerCase() + "%20alvarez", lider);
        assertThat(porNombre.get("total").asInt()).isEqualTo(1);
        assertThat(porNombre.get("items").get(0).get("personId").asText()).isEqualTo(beto.toString());

        JsonNode lejos = obtener("/api/v1/leadership/people?q=" + marca + "&page=5&size=1", admin);
        assertThat(lejos.get("items")).isEmpty();
        assertThat(lejos.get("total").asInt()).isEqualTo(2);
        assertThat(pedir("/api/v1/leadership/people?size=0", lider).statusCode()).isEqualTo(400);
    }

    // ── la ficha ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el líder abre la semana de quien NO tiene grupo: 200, sin grupo ni mentor, y la consulta queda con el grupo en NULL")
    void semanaSinGrupo() throws Exception {
        long antes = consultasDe(beto);

        HttpResponse<String> respuesta = pedir("/api/v1/leadership/people/" + beto + "/week", lider);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode ficha = json.readTree(respuesta.body());
        assertThat(ficha.get("personaId").asText()).isEqualTo(beto.toString());
        assertThat(ficha.get("grupoId").isNull()).isTrue();
        assertThat(ficha.get("mentor").isNull()).isTrue();
        assertThat(ficha.get("puedeAvisarAlMentor").asBoolean()).isFalse();
        assertThat(ficha.get("dias")).hasSize(7);
        assertThat(consultasDe(beto)).isEqualTo(antes + 1);
        Map<String, Object> fila = jdbc.queryForMap("SELECT rol_consultor, grupo_id, mentor_id, consultor_id "
                + "FROM renaser.consultas_de_ficha WHERE persona_id = ? ORDER BY consultada_en DESC LIMIT 1", beto);
        assertThat(fila.get("rol_consultor")).isEqualTo("MENTOR_LEAD");
        assertThat(fila.get("consultor_id")).isEqualTo(lider);
        assertThat(fila.get("grupo_id")).isNull();
        assertThat(fila.get("mentor_id")).isNull();
    }

    @Test
    @DisplayName("para quien tiene grupo con mentor, el cuerpo de /people/{p}/week es IGUAL al de /groups/{g}/people/{p}/week, y se avisa al mentor")
    void igualQuePorElGrupo() throws Exception {
        String porGrupo = pedir("/api/v1/leadership/groups/" + fenix + "/people/" + ana + "/week", lider).body();
        String sinGrupo = pedir("/api/v1/leadership/people/" + ana + "/week", lider).body();

        assertThat(json.readTree(sinGrupo)).isEqualTo(json.readTree(porGrupo));
        JsonNode ficha = json.readTree(sinGrupo);
        assertThat(ficha.get("grupoId").asText()).isEqualTo(fenix.toString());
        assertThat(ficha.get("mentor").get("id").asText()).isEqualTo(mentora.toString());
        assertThat(ficha.get("puedeAvisarAlMentor").asBoolean()).isTrue();
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT grupo_id, mentor_id FROM renaser.consultas_de_ficha "
                + "WHERE persona_id = ? AND consultor_id = ?", ana, lider);
        assertThat(filas).hasSize(2).allSatisfy(fila -> {
            assertThat(fila.get("grupo_id")).isEqualTo(fenix);
            assertThat(fila.get("mentor_id")).isEqualTo(mentora);
        });
    }

    @Test
    @DisplayName("la ventana se pide con «semanaHasta», como en la ruta por grupo: un viernes pasado da esa semana de sábado a viernes, igual por las dos rutas")
    void semanaConSemanaHasta() throws Exception {
        LocalDate viernes = LocalDate.now(ZoneId.of("America/Lima")).with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));
        String fecha = "?semanaHasta=" + viernes;

        JsonNode sinGrupo = obtener("/api/v1/leadership/people/" + ana + "/week" + fecha, lider);
        JsonNode porGrupo = obtener("/api/v1/leadership/groups/" + fenix + "/people/" + ana + "/week" + fecha, lider);

        assertThat(sinGrupo.get("hasta").asText()).isEqualTo(viernes.toString());
        assertThat(sinGrupo.get("desde").asText()).isEqualTo(viernes.minusDays(6).toString());
        assertThat(sinGrupo.get("cerrada").asBoolean()).isTrue();
        assertThat(sinGrupo).isEqualTo(porGrupo);
        assertThat(obtener("/api/v1/leadership/people/" + beto + "/week" + fecha, lider).get("hasta").asText())
                .isEqualTo(viernes.toString());
        // Un día que no es viernes se rechaza con 400, también sin grupo.
        assertThat(pedir("/api/v1/leadership/people/" + beto + "/week?semanaHasta=" + viernes.minusDays(1), lider)
                .statusCode()).isEqualTo(400);
        // Huecos y cierres piden la semana con «semana», como sus rutas por grupo.
        LocalDate lunes = LocalDate.now(ZoneId.of("America/Lima")).with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        assertThat(obtener("/api/v1/leadership/people/" + beto + "/huecos?semana=" + lunes, lider).get("semana").asText())
                .isEqualTo(lunes.toString());
        assertThat(obtener("/api/v1/leadership/people/" + beto + "/cierres?semana=" + lunes, lider).get("semana").asText())
                .isEqualTo(lunes.toString());
    }

    @Test
    @DisplayName("el Radar, los huecos y los cierres de quien no tiene grupo se leen (200); huecos y cierres dejan su consulta, el Radar no")
    void radarHuecosYCierres() throws Exception {
        assertThat(pedir("/api/v1/leadership/people/" + beto + "/radar", lider).statusCode()).isEqualTo(200);
        assertThat(consultasDe(beto)).isZero();

        assertThat(pedir("/api/v1/leadership/people/" + beto + "/huecos", lider).statusCode()).isEqualTo(200);
        assertThat(pedir("/api/v1/leadership/people/" + beto + "/cierres", admin).statusCode()).isEqualTo(200);

        assertThat(consultasDe(beto)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT DISTINCT rol_consultor FROM renaser.consultas_de_ficha WHERE persona_id = ?",
                String.class, beto)).containsExactlyInAnyOrder("MENTOR_LEAD", "ADMIN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id = ? "
                + "AND grupo_id IS NULL", Long.class, beto)).isEqualTo(2);
    }

    // ── autorización negativa ───────────────────────────────────────────────

    @Test
    @DisplayName("un mentor (también el del grupo), una guía, un aprendiz y Soporte TI reciben 403 en la lista y en las cuatro lecturas; no queda registro")
    void sinPermiso() throws Exception {
        for (UUID actor : List.of(otroMentor, mentora, guia, aprendizActor, soporteTi)) {
            assertThat(pedir("/api/v1/leadership/people", actor).statusCode()).as("lista, " + actor).isEqualTo(403);
            for (String parte : List.of("week", "radar", "huecos", "cierres")) {
                assertThat(pedir("/api/v1/leadership/people/" + beto + "/" + parte, actor).statusCode())
                        .as(parte + ", " + actor).isEqualTo(403);
            }
        }
        assertThat(consultasDe(beto)).isZero();
    }

    @Test
    @DisplayName("un líder con la sesión viva que pasa a suspendido deja de ver a todos: 403")
    void liderSuspendido() throws Exception {
        String sesion = sesionDe(lider);
        assertThat(pedir("/api/v1/leadership/people/" + beto + "/week", sesion).statusCode()).isEqualTo(200);

        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", lider);

        assertThat(pedir("/api/v1/leadership/people", sesion).statusCode()).isEqualTo(403);
        assertThat(pedir("/api/v1/leadership/people/" + beto + "/week", sesion).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("una cuenta de prueba, una suspendida, una mentora y un id que no existe son 404 y no dejan registro")
    void noSeMuestra() throws Exception {
        for (UUID persona : List.of(prueba, suspendida, mentora, UUID.randomUUID())) {
            for (String parte : List.of("week", "radar", "huecos", "cierres")) {
                assertThat(pedir("/api/v1/leadership/people/" + persona + "/" + parte, lider).statusCode())
                        .as(parte + ", " + persona).isEqualTo(404);
            }
            assertThat(consultasDe(persona)).isZero();
        }
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private long consultasDe(UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id = ?", Long.class,
                persona);
    }

    private JsonNode obtener(String ruta, UUID quien) throws Exception {
        HttpResponse<String> respuesta = pedir(ruta, quien);
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String ruta, UUID quien) throws Exception {
        return pedir(ruta, sesionDe(quien));
    }

    private HttpResponse<String> pedir(String ruta, String sesion) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", sesion).GET().build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
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

    private UUID usuario(String rol, String nombre, String estado, boolean dePrueba) {
        UUID id = UUID.randomUUID();
        // Una cuenta de prueba lleva el correo reservado y no tiene contraseña (CHECK de V117).
        String correo = dePrueba ? "ver-como+" + id + "@renaser.invalid" : id + "@renaser.test";
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, es_cuenta_de_prueba)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario), ?)
                """, id, correo, nombre, rol, estado, dePrueba);
        usuarios.add(id);
        return id;
    }

    private void programa(UUID id) {
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, "
                + "programa_activado_en, timezone) VALUES (?, 21, CURRENT_DATE - 20, now() - interval '21 days', "
                + "'America/Lima')", id);
    }

    private UUID grupo(String nombre, UUID mentor) {
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?) ON CONFLICT DO NOTHING", mentor);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20, ?)
                """, id, nombre, cohorte, mentor);
        grupos.add(id);
        return id;
    }

    private void asignar(UUID grupo, UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 hour', NULL, 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, "prueba|" + id);
    }
}

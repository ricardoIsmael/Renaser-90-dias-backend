package com.renaser.os.leadership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Hábitos de todos» (spec 029) de punta a punta: Tomcat real, la sesión de verdad, el filtro de permisos y Postgres con las migraciones. Lo que solo se
 * ve así: que las cifras salgan de {@code registros_habito}, que NADA de otra formación cruce (alumnos, registros ni el acceso por id a un hábito), que los
 * 403 los dé el guard y que «hoy» sea el día de Lima con el reloj en la madrugada UTC.
 *
 * <p>Semilla: la Escuela con Ana y Beto (grupo «Fénix») y Carla (sin grupo); el Diplomado con Dani. El hábito «Despertar» es de la Escuela y «Estudio» del
 * Diplomado. El reloj está a las 03:00 UTC del 31/10, que todavía es el 30 en Lima: ese es «hoy».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class HabitosDeTodosIT {

    private static final Instant MADRUGADA_UTC = Instant.parse("2026-10-31T03:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();
    private final List<UUID> habitos = new ArrayList<>();
    private final List<String> categorias = new ArrayList<>();

    private String marca;
    private UUID lider;
    private UUID admin;
    private UUID mentora;
    private UUID guia;
    private UUID aprendizActor;
    private UUID soporteTi;
    private UUID ana;
    private UUID beto;
    private UUID carla;
    private UUID dani;
    private UUID fenix;
    private UUID cohorte;
    private UUID despertar;
    private UUID estudio;

    @BeforeEach
    void sembrar() {
        reloj.fijar(MADRUGADA_UTC);
        marca = "HT" + UUID.randomUUID().toString().substring(0, 8);
        lider = usuario("LIDER_MENTORES", "Líder " + marca);
        admin = usuario("ADMIN", "Admin " + marca);
        mentora = usuario("MENTOR", "Luisa " + marca);
        guia = usuario("GUIA_APRENDIZ", "Guía " + marca);
        aprendizActor = usuario("APRENDIZ", "Actor " + marca);
        soporteTi = usuario("SOPORTE_TI", "Soporte " + marca);
        ana = usuario("APRENDIZ", "Ana Pérez " + marca);
        beto = usuario("APRENDIZ", "Beto Álvarez " + marca);
        carla = usuario("APRENDIZ", "Carla Díaz " + marca);
        dani = usuario("APRENDIZ", "Dani Gómez " + marca);
        List.of(ana, beto, carla).forEach(id -> programa(id, "ESCUELA"));
        programa(dani, "DIPLOMADO");
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, ?, ?)", cohorte, "Cohorte " + marca, HOY.minusDays(60));
        fenix = grupo("Fénix " + marca, mentora);
        asignar(fenix, mentora, "MENTOR");
        asignar(fenix, ana, "APRENDIZ");
        asignar(fenix, beto, "APRENDIZ");

        String categoriaDiplomado = "DIPL_" + marca.toUpperCase(); // la clave de una categoría va en mayúsculas (CHECK de la tabla)
        categorias.add(categoriaDiplomado);
        jdbc.update("INSERT INTO renaser.categorias_habito (clave, nombre, formacion, orden) VALUES (?, 'Estudio', 'DIPLOMADO', 1) ON CONFLICT DO NOTHING",
                categoriaDiplomado);
        despertar = habito("Despertar " + marca, "CUERPO", "ESCUELA");
        estudio = habito("Estudio " + marca, categoriaDiplomado, "DIPLOMADO");
        jdbc.update("INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, tipo_dia, hora_disparo, hora_limite) VALUES (?, 1, 'TODOS', '06:00', '22:00')",
                despertar);

        registro(ana, despertar, HOY, "COMPLETADO", false, Instant.parse("2026-10-30T11:42:00Z"));
        registro(beto, despertar, HOY, "PENDIENTE", false, null);
        registro(carla, despertar, HOY, "PENDIENTE", false, null);
        registro(ana, despertar, HOY.minusDays(1), "COMPLETADO", false, Instant.parse("2026-10-29T11:00:00Z"));
        registro(beto, despertar, HOY.minusDays(1), "EXPIRADO", false, null);
        registro(carla, despertar, HOY.minusDays(1), "EXPIRADO", true, null);
        registro(dani, estudio, HOY, "COMPLETADO", false, Instant.parse("2026-10-30T14:00:00Z"));
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id));
        habitos.forEach(id -> {
            jdbc.update("DELETE FROM renaser.horarios_habito WHERE habito_id = ?", id);
            jdbc.update("DELETE FROM renaser.habitos WHERE id = ?", id);
        });
        categorias.forEach(clave -> jdbc.update("DELETE FROM renaser.categorias_habito WHERE clave = ?", clave));
        grupos.forEach(id -> {
            jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id);
            jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id);
        });
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("el líder de la Escuela ve SUS hábitos con las cifras del día; «hoy» es el día de Lima con el reloj en la madrugada UTC y lo no hecho es «pendiente»")
    void resumenDeHoy() throws Exception {
        HttpResponse<String> respuesta = pedir("/api/v1/leadership/habits", lider);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode dia = json.readTree(respuesta.body());
        assertThat(dia.get("date").asText()).isEqualTo("2026-10-30");
        assertThat(dia.get("isToday").asBoolean()).isTrue();
        assertThat(dia.get("earliest").asText()).isEqualTo("2026-10-23");
        JsonNode habitosDelDia = dia.get("habits");
        assertThat(habitosDelDia).hasSize(1);
        JsonNode elDespertar = habitosDelDia.get(0);
        assertThat(elDespertar.get("habitId").asText()).isEqualTo(despertar.toString());
        assertThat(elDespertar.get("category").asText()).isEqualTo("BODY");
        assertThat(elDespertar.get("formation").asText()).isEqualTo("ESCUELA");
        assertThat(elDespertar.get("expected").asInt()).isEqualTo(3);
        assertThat(elDespertar.get("done").asInt()).isEqualTo(1);
        assertThat(elDespertar.get("pending").asInt()).isEqualTo(2);
        assertThat(elDespertar.get("missed").asInt()).isZero();
        assertThat(dia.get("totals").get("students").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(respuesta.body()).doesNotContain(estudio.toString()).doesNotContain("Dani");
    }

    @Test
    @DisplayName("un día cerrado: lo no cumplido es «sin cumplir» y lo opcional que no se hizo no cuenta; las cifras cuadran con los registros")
    void ayer() throws Exception {
        JsonNode dia = obtener("/api/v1/leadership/habits?date=2026-10-29", lider);

        assertThat(dia.get("isToday").asBoolean()).isFalse();
        JsonNode elDespertar = dia.get("habits").get(0);
        assertThat(elDespertar.get("expected").asInt()).isEqualTo(2);
        assertThat(elDespertar.get("done").asInt()).isEqualTo(1);
        assertThat(elDespertar.get("missed").asInt()).isEqualTo(1);
        assertThat(elDespertar.get("pending").asInt()).isZero();
        Integer enLaBase = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.registros_habito WHERE habito_id = ? AND fecha_ejecucion = ? "
                + "AND NOT es_opcional", Integer.class, despertar, HOY.minusDays(1));
        assertThat(enLaBase).isEqualTo(elDespertar.get("expected").asInt());
    }

    @Test
    @DisplayName("nada de otra formación cruza: el líder de la Escuela no ve a Dani, ni el hábito del Diplomado, aunque lo pida con el filtro o por id")
    void aislamiento() throws Exception {
        JsonNode pidiendoElDiplomado = obtener("/api/v1/leadership/habits?formation=DIPLOMADO", lider);
        assertThat(pidiendoElDiplomado.get("habits")).isEmpty();
        assertThat(pidiendoElDiplomado.get("totals").get("students").asInt()).isZero();

        assertThat(pedir("/api/v1/leadership/habits/" + estudio + "/people", lider).statusCode()).isEqualTo(404);
        // El mismo código que un hábito que no existe.
        assertThat(pedir("/api/v1/leadership/habits/" + UUID.randomUUID() + "/people", lider).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Administración ve las tres formaciones y puede separarlas con el filtro; el líder con otra formación activa ve solo esa")
    void adminYLiderConDosFormaciones() throws Exception {
        JsonNode todas = obtener("/api/v1/leadership/habits", admin);
        assertThat(todas.get("habits")).extracting(h -> h.get("habitId").asText()).contains(despertar.toString(), estudio.toString());

        JsonNode soloDiplomado = obtener("/api/v1/leadership/habits?formation=DIPLOMADO", admin);
        assertThat(soloDiplomado.get("habits")).extracting(h -> h.get("habitId").asText()).contains(estudio.toString())
                .doesNotContain(despertar.toString());
        assertThat(soloDiplomado.get("totals").get("students").asInt()).isGreaterThanOrEqualTo(1);

        jdbc.update("INSERT INTO renaser.formaciones_personal (usuario_id, formacion, activa) VALUES (?, 'DIPLOMADO', false)", lider);
        jdbc.update("UPDATE renaser.formaciones_personal SET activa = false WHERE usuario_id = ?", lider);
        jdbc.update("UPDATE renaser.formaciones_personal SET activa = true WHERE usuario_id = ? AND formacion = 'DIPLOMADO'", lider);
        JsonNode delLiderAhora = obtener("/api/v1/leadership/habits", lider);
        assertThat(delLiderAhora.get("habits")).extracting(h -> h.get("habitId").asText()).containsExactly(estudio.toString());
        assertThat(delLiderAhora.get("habits").toString()).doesNotContain(despertar.toString());
    }

    @Test
    @DisplayName("las personas de un hábito: quién no cumplió primero, la hora planificada, la hora en que cumplió y «sin datos» sin ser una falta")
    void personasDelHabito() throws Exception {
        JsonNode cuerpo = obtener("/api/v1/leadership/habits/" + despertar + "/people?q=" + marca, lider);

        assertThat(cuerpo.get("habit").get("title").asText()).isEqualTo("Despertar " + marca);
        assertThat(cuerpo.get("total").asInt()).isEqualTo(3);
        JsonNode gente = cuerpo.get("people");
        assertThat(gente).extracting(p -> p.get("status").asText()).containsExactly("PENDIENTE", "PENDIENTE", "CUMPLIDO");
        JsonNode laQueCumplio = gente.get(2);
        assertThat(laQueCumplio.get("personId").asText()).isEqualTo(ana.toString());
        assertThat(laQueCumplio.get("doneAt").asText()).isEqualTo("06:42");
        assertThat(laQueCumplio.get("plannedTime").asText()).startsWith("06:00");
        assertThat(laQueCumplio.get("groupName").asText()).isEqualTo("Fénix " + marca);
        assertThat(gente.get(0).get("groupId").isNull() || gente.get(1).get("groupId").isNull()).isTrue();
        assertThat(cuerpo.toString()).doesNotContain("@renaser.test").doesNotContain("email");
    }

    // Auditoria de consultas del 2026-11-02 (H-01): la lista de personas de un habito pedia el programa y el horario de CADA fila (~20 consultas por persona:
    // ~630 por pagina de 30, ~2.100 por una de 100). Ahora son pocas consultas para toda la pagina, y su numero no depende de cuantas filas haya.
    @Test
    @DisplayName("las personas de un hábito: la cantidad de sentencias no crece con las filas (3 personas o 15 dan las mismas) y la hora de cada una sigue saliendo")
    void personasDelHabitoNoHacenUnaConsultaPorFila() throws Exception {
        String ruta = "/api/v1/leadership/habits/" + despertar + "/people?q=" + marca + "&size=100";
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        obtener(ruta, lider); // calienta lo que se arma una sola vez (permisos, cachés)

        estadisticas.clear();
        JsonNode pocas = obtener(ruta, lider);
        long sentenciasConTres = estadisticas.getPrepareStatementCount();

        for (int i = 0; i < 12; i++) {
            UUID otra = usuario("APRENDIZ", "Extra " + i + " " + marca);
            programa(otra, "ESCUELA");
            registro(otra, despertar, HOY, i % 3 == 0 ? "COMPLETADO" : "PENDIENTE", false, i % 3 == 0 ? Instant.parse("2026-10-30T11:30:00Z") : null);
        }
        estadisticas.clear();
        JsonNode muchas = obtener(ruta, lider);
        long sentenciasConQuince = estadisticas.getPrepareStatementCount();

        assertThat(pocas.get("total").asInt()).isEqualTo(3);
        assertThat(muchas.get("total").asInt()).isEqualTo(15);
        assertThat(sentenciasConQuince).as("sentencias con 15 personas contra 3").isEqualTo(sentenciasConTres);
        // La hora planificada (el horario del catalogo, 06:00) sigue saliendo en cada fila con registro.
        assertThat(muchas.get("people")).allSatisfy(persona -> assertThat(persona.get("plannedTime").asText()).startsWith("06:00"));
    }

    @Test
    @DisplayName("filtrar por grupo y por «sin grupo» ajusta alumnos y cifras")
    void filtrosPorGrupo() throws Exception {
        JsonNode delGrupo = obtener("/api/v1/leadership/habits?group=" + fenix, lider);
        assertThat(delGrupo.get("totals").get("students").asInt()).isEqualTo(2);
        assertThat(delGrupo.get("habits").get(0).get("expected").asInt()).isEqualTo(2);

        JsonNode sinGrupo = obtener("/api/v1/leadership/habits?group=none", lider);
        assertThat(sinGrupo.get("totals").get("students").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(sinGrupo.get("habits").get(0).get("expected").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("la ventana: el futuro (mañana UTC) y más de 7 días atrás se rechazan con 400")
    void ventana() throws Exception {
        assertThat(pedir("/api/v1/leadership/habits?date=2026-10-31", lider).statusCode()).isEqualTo(400);
        assertThat(pedir("/api/v1/leadership/habits?date=2026-10-22", lider).statusCode()).isEqualTo(400);
        assertThat(pedir("/api/v1/leadership/habits?date=2026-10-23", lider).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("un mentor (también el del grupo), una guía, un aprendiz y Soporte TI reciben 403 en las dos lecturas; una sesión viva de un líder suspendido, también")
    void permisos() throws Exception {
        for (UUID actor : List.of(mentora, guia, aprendizActor, soporteTi)) {
            assertThat(pedir("/api/v1/leadership/habits", actor).statusCode()).as("resumen, " + actor).isEqualTo(403);
            assertThat(pedir("/api/v1/leadership/habits/" + despertar + "/people", actor).statusCode()).as("personas, " + actor).isEqualTo(403);
        }
        String sesion = sesionDe(lider);
        assertThat(pedir("/api/v1/leadership/habits", sesion).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE renaser.usuarios SET estado = CAST('SUSPENDIDO' AS renaser.estado_usuario) WHERE id = ?", lider);
        assertThat(pedir("/api/v1/leadership/habits", sesion).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("mirar las listas no deja constancia de consulta de nadie")
    void sinConstancia() throws Exception {
        Integer antes = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.consultas_de_ficha WHERE consultor_id = ?", Integer.class, lider);

        obtener("/api/v1/leadership/habits", lider);
        obtener("/api/v1/leadership/habits/" + despertar + "/people", lider);

        Integer despues = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.consultas_de_ficha WHERE consultor_id = ?", Integer.class, lider);
        assertThat(despues).isEqualTo(antes);
    }

    // ── Fixture ─────────────────────────────────────────────────────────────

    private JsonNode obtener(String ruta, UUID quien) throws Exception {
        HttpResponse<String> respuesta = pedir(ruta, quien);
        assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
        return json.readTree(respuesta.body());
    }

    private HttpResponse<String> pedir(String ruta, UUID quien) throws Exception {
        return pedir(ruta, sesionDe(quien));
    }

    private HttpResponse<String> pedir(String ruta, String sesion) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).header("X-Auth-Token", sesion).GET().build();
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

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        com.renaser.os.FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        usuarios.add(id);
        return id;
    }

    private void programa(UUID id, String formacion) {
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone, formacion)
                VALUES (?, 21, ?, ?, 'America/Lima', ?)
                """, id, HOY.minusDays(20), Timestamp.from(Instant.parse("2026-10-09T15:00:00Z")), formacion);
    }

    private UUID habito(String titulo, String categoria, String formacion) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.habitos (id, titulo, categoria_clave, formacion) VALUES (?, ?, ?, ?)", id, titulo, categoria, formacion);
        habitos.add(id);
        return id;
    }

    private void registro(UUID persona, UUID habito, LocalDate fecha, String estado, boolean opcional, Instant completado) {
        jdbc.update("""
                INSERT INTO renaser.registros_habito (participante_id, habito_id, fecha_ejecucion, dia_programa, tipo_dia, es_opcional, estado, completado_en)
                VALUES (?, ?, ?, 15, CAST('TODOS' AS renaser.tipo_dia), ?, CAST(? AS renaser.estado_registro), ?)
                """, persona, habito, fecha, opcional, estado, completado == null ? null : Timestamp.from(completado));
    }

    private UUID grupo(String nombre, UUID mentor) {
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?) ON CONFLICT DO NOTHING", mentor);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), ?, ?, ?)
                """, id, nombre, cohorte, HOY.minusDays(5), HOY.plusDays(20), mentor);
        grupos.add(id);
        return id;
    }

    private void asignar(UUID grupo, UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), ?, NULL, 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, Timestamp.from(MADRUGADA_UTC.minusSeconds(86_400)), "prueba|" + id);
    }
}

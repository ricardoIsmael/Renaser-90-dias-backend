package com.renaser.os.leadership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El escenario de las pruebas de integración de las lecturas del líder de la spec 056 sobre el Tomcat real, con la sesión de verdad y todo
 * en Postgres. Hoy es el miércoles 18 de noviembre de 2026 a las 10:00 en Lima.
 *
 * <p>La Escuela: el grupo «Fénix» con su mentora Luisa y su aprendiz Ana (Día 1 el lunes 5 de octubre); Beto, aprendiz sin grupo; un
 * mentor que lleva su propio programa y otro que no; el líder y Administración. Dani cursa el Diplomado: el líder de la Escuela no lo ve.
 *
 * <p>Se pide con el token de sesión de cada persona, nunca con el header {@code X-Actor-Id}.
 *
 * <p>El almacenamiento es uno en memoria ({@link AlmacenamientoDeLasPruebasDelLider}): las pruebas del documento semanal (spec 056) leen lo que se subió.
 * Vive aquí y no en cada prueba para que todas compartan UN contexto de Spring (E-649).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, AlmacenamientoDeLasPruebasDelLider.class})
abstract class EscenarioDelLiderIT {

    /** Miércoles 18 de noviembre de 2026, 10:00 en Lima. */
    static final Instant AHORA = Instant.parse("2026-11-18T15:00:00Z");
    static final String DIA_UNO = "2026-10-05";

    @LocalServerPort
    int puerto;
    @Autowired
    SessionRepository<? extends Session> sesiones;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RelojDePrueba reloj;

    final HttpClient http = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();

    UUID cohorte;
    UUID fenix;
    UUID lider;
    UUID admin;
    UUID mentora;
    UUID mentorConPrograma;
    UUID mentorSinPrograma;
    UUID ana;
    UUID beto;
    UUID dani;

    @BeforeEach
    void sembrarElEscenario() {
        reloj.fijar(AHORA);
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte spec 056', DATE '2026-09-19')", cohorte);
        lider = usuario("LIDER_MENTORES", "Líder Uno", "ACTIVO");
        admin = usuario("ADMIN", "Kelin Rojas", "ACTIVO");
        mentora = usuario("MENTOR", "Luisa Rojas", "ACTIVO");
        mentorConPrograma = usuario("MENTOR", "Marco Sin Grupo", "ACTIVO");
        mentorSinPrograma = usuario("MENTOR", "Nora Sin Programa", "ACTIVO");
        ana = usuario("APRENDIZ", "Ana Pérez", "ACTIVO");
        beto = usuario("APRENDIZ", "Beto Álvarez", "ACTIVO");
        dani = usuario("APRENDIZ", "Dani Gómez", "ACTIVO");
        programa(ana, DIA_UNO, "ESCUELA");
        programa(beto, DIA_UNO, "ESCUELA");
        programa(dani, DIA_UNO, "DIPLOMADO");
        programa(mentorConPrograma, DIA_UNO, "ESCUELA");
        fenix = grupo("Fénix", mentora);
        asignar(fenix, mentora, "MENTOR");
        asignar(fenix, ana, "APRENDIZ");
    }

    @AfterEach
    void limpiarElEscenario() {
        grupos.forEach(id -> {
            jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id);
            jdbc.update("DELETE FROM renaser.asignaciones_celula WHERE celula_id = ?", id);
            jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id);
        });
        usuarios.reversed().forEach(id -> {
            jdbc.update("DELETE FROM renaser.semaforo_dias WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.semaforo_pausas WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM renaser.registros_habito WHERE participante_id = ?", id);
            jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id);
        });
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    // ── semilla ─────────────────────────────────────────────────────────────

    UUID usuario(String rol, String nombre, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre, rol, estado);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        usuarios.add(id);
        return id;
    }

    /** El Día 1 elegido (programa activado) en la zona de Lima, en esa formación. */
    void programa(UUID persona, String diaUno, String formacion) {
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, "
                + "timezone, formacion) VALUES (?, 44, CAST(? AS date), CAST(? AS date) - 1, 'America/Lima', ?)",
                persona, diaUno, diaUno, formacion);
    }

    UUID grupo(String nombre, UUID mentor) {
        return grupo(nombre, mentor, "ESCUELA");
    }

    /** Un grupo regular en curso hoy; {@code mentor} null = sin mentor. */
    UUID grupo(String nombre, UUID mentor, String formacion) {
        if (mentor != null) {
            jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?) ON CONFLICT DO NOTHING", mentor);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id, formacion)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), DATE '2026-10-19', DATE '2027-01-17', ?, ?)
                """, id, nombre, cohorte, mentor, formacion);
        grupos.add(id);
        return id;
    }

    void asignar(UUID grupo, UUID persona, String funcion) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), TIMESTAMPTZ '2026-11-17 15:00:00+00', NULL, 'ADMINISTRATIVO', ?)
                """, id, grupo, persona, funcion, "prueba|" + id);
    }

    /** Un hábito del catálogo de la categoría dada y su registro del día. */
    UUID habitoDelDia(UUID persona, String fecha, String titulo, String categoria, String estado) {
        UUID habito = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave, exigencia_evidencia) "
                + "VALUES (?, 'SISTEMA', ?, 'CHECKBOX', ?, CAST('OPCIONAL' AS renaser.exigencia_evidencia))",
                habito, titulo + " " + habito, categoria);
        UUID registro = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.registros_habito (id, participante_id, habito_id, fecha_ejecucion, dia_programa, "
                + "tipo_dia, estado) VALUES (?, ?, ?, CAST(? AS date), 30, CAST('DISCIPLINA' AS renaser.tipo_dia), "
                + "CAST(? AS renaser.estado_registro))", registro, persona, habito, fecha, estado);
        return registro;
    }

    /** Un día ya cerrado del semáforo: {@code cumplidos} de {@code programados} hábitos. */
    void diaDelSemaforo(UUID persona, String fecha, int programados, int cumplidos) {
        jdbc.update("INSERT INTO renaser.semaforo_dias (participante_id, fecha, habitos_programados, habitos_cumplidos, "
                + "objetivos_programados, objetivos_cumplidos, calculado_en) VALUES (?, CAST(? AS date), ?, ?, 0, 0, now())",
                persona, fecha, programados, cumplidos);
    }

    long consultasDe(UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.consultas_de_ficha WHERE persona_id = ?", Long.class, persona);
    }

    // ── pedidos ─────────────────────────────────────────────────────────────

    String sesionDe(UUID usuario) {
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

    HttpResponse<String> pedir(String metodo, String ruta, String sesion, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", sesion);
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String ruta, UUID quien) throws Exception {
        return pedir("GET", ruta, sesionDe(quien), null);
    }

    HttpResponse<String> get(String ruta, String sesion) throws Exception {
        return pedir("GET", ruta, sesion, null);
    }

    JsonNode cuerpo(HttpResponse<String> respuesta) throws Exception {
        return json.readTree(respuesta.body());
    }

    String codigo(HttpResponse<String> respuesta) throws Exception {
        JsonNode nodo = cuerpo(respuesta).get("codigo");
        return nodo == null ? null : nodo.asText();
    }

    String delLider(UUID persona, String recurso) {
        return "/api/v1/leadership/people/" + persona + "/" + recurso;
    }

    String delMentor(UUID grupo, UUID aprendiz, String recurso) {
        return "/api/v1/mentor/groups/" + grupo + "/learners/" + aprendiz + "/" + recurso;
    }
}

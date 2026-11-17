package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

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
 * El escenario de las pruebas de integración de la ficha del mentor (spec 055) sobre el Tomcat real, con sesión de verdad y todo
 * en Postgres: el grupo «Fénix» con su mentora y su aprendiz Ana (Día 1 el lunes 5 de octubre), otro grupo con otra mentora, y
 * quienes no tienen nada que ver (líder, Administración, la propia aprendiz). Hoy es el miércoles 18 de noviembre de 2026 a las
 * 10:00 en Lima.
 *
 * <p>Se pide con el token de sesión de cada persona, nunca con el header {@code X-Actor-Id}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class EscenarioDelMentorIT {

    /** Miércoles 18 de noviembre de 2026, 10:00 en Lima. */
    static final Instant AHORA = Instant.parse("2026-11-18T15:00:00Z");

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
    UUID aurora;
    UUID mentora;
    UUID otraMentora;
    UUID ana;
    UUID lider;
    UUID admin;

    @BeforeEach
    void sembrarElEscenario() {
        reloj.fijar(AHORA);
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte spec 055', CURRENT_DATE - 60)", cohorte);
        mentora = usuario("MENTOR", "Luisa Rojas", "ACTIVO");
        otraMentora = usuario("MENTOR", "Marta Otra", "ACTIVO");
        ana = usuario("APRENDIZ", "Ana Pérez", "ACTIVO");
        lider = usuario("LIDER_MENTORES", "Líder Uno", "ACTIVO");
        admin = usuario("ADMIN", "Kelin Rojas", "ACTIVO");
        programa(ana, "2026-10-05");
        fenix = grupo("Fénix", mentora);
        aurora = grupo("Aurora", otraMentora);
        asignar(fenix, mentora, "MENTOR");
        asignar(fenix, ana, "APRENDIZ");
        asignar(aurora, otraMentora, "MENTOR");
    }

    @AfterEach
    void limpiarElEscenario() {
        jdbc.update("DELETE FROM renaser.notas_del_mentor_versiones WHERE nota_id IN "
                + "(SELECT id FROM renaser.notas_del_mentor WHERE aprendiz_id = ?)", ana);
        jdbc.update("DELETE FROM renaser.notas_del_mentor WHERE aprendiz_id = ?", ana);
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

    /** El Día 1 elegido (activado) en la zona de Lima. */
    void programa(UUID aprendiz, String diaUno) {
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone) "
                + "VALUES (?, 44, CAST(? AS date), CAST(? AS date) - 1, 'America/Lima')", aprendiz, diaUno, diaUno);
    }

    UUID grupo(String nombre, UUID mentor) {
        jdbc.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?) ON CONFLICT DO NOTHING", mentor);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 30, CURRENT_DATE + 60, ?)
                """, id, nombre, cohorte, mentor);
        grupos.add(id);
        return id;
    }

    void asignar(UUID grupo, UUID usuario, String funcion) {
        asignarDesde(grupo, usuario, funcion, "now() - interval '1 hour'");
    }

    /** @param inicio expresión SQL del inicio de la asignación */
    void asignarDesde(UUID grupo, UUID usuario, String funcion, String inicio) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), %s, NULL, 'ADMINISTRATIVO', ?)
                """.formatted(inicio), id, grupo, usuario, funcion, "prueba|" + id);
    }

    /** Termina hoy la asignación del usuario en el grupo: ya no lo acompaña ni es de él. */
    void terminarAsignacion(UUID grupo, UUID usuario) {
        jdbc.update("UPDATE renaser.asignaciones_celula SET fin = now() - interval '30 minutes' WHERE celula_id = ? AND usuario_id = ?", grupo, usuario);
        jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ? AND mentor_id = ?", grupo, usuario);
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

    HttpResponse<String> pedir(String metodo, String ruta, UUID quien, Object cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", sesionDe(quien));
        if (cuerpo == null) {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            pedido.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String ruta, UUID quien) throws Exception {
        return pedir("GET", ruta, quien, null);
    }

    JsonNode cuerpo(HttpResponse<String> respuesta) throws Exception {
        return json.readTree(respuesta.body());
    }

    String codigo(HttpResponse<String> respuesta) throws Exception {
        JsonNode nodo = cuerpo(respuesta).get("codigo");
        return nodo == null ? null : nodo.asText();
    }

    String delMentor(UUID grupo, UUID aprendiz, String recurso) {
        return "/api/v1/mentor/groups/" + grupo + "/learners/" + aprendiz + "/" + recurso;
    }
}

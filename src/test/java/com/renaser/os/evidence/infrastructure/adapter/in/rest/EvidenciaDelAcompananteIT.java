package com.renaser.os.evidence.infrastructure.adapter.in.rest;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #167: quién puede abrir UNA evidencia ({@code GET /api/v1/evidence/{id}} y {@code /url}) cuando
 * quien pide es el mentor de la persona. La fuente de verdad es la asignación vigente del grupo
 * ({@code asignaciones_celula}, la misma que usa la ficha del mentor), no {@code participantes_programa.mentor_id},
 * que puede estar vacío o ser de un mentor anterior.
 *
 * <p>Sobre el Tomcat real, con sesión de verdad y Postgres con las filas sembradas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EvidenciaDelAcompananteIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();
    private UUID cohorte;

    private UUID grupoDeAna;
    private UUID ana;
    private UUID fotoDeAna;
    private UUID textoDeAna;

    @BeforeEach
    void seed() {
        cohorte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte #167', CURRENT_DATE - 10)",
                cohorte);
        // Ana NO tiene mentor_id en participantes_programa: es el caso del issue.
        ana = usuario("APRENDIZ", "Ana Pérez");
        jdbcTemplate.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, mentor_id) VALUES (?, 20, NULL)", ana);
        grupoDeAna = grupo("Fenix 167");
        asignar(grupoDeAna, ana, "APRENDIZ", "now() - interval '9 days'", null);
        UUID roca = rocaDeHoy(ana);
        fotoDeAna = evidencia(ana, roca, "FOTO", "evidencia-rocas/" + ana + "/" + roca + "/" + UUID.randomUUID(), null);
        textoDeAna = evidencia(ana, roca, "TEXTO", null, "Caminé 30 minutos");
    }

    @AfterEach
    void limpiar() {
        grupos.forEach(id -> jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id));
        grupos.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        usuarios.reversed().forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("el mentor que acompaña HOY abre el detalle y la URL de la evidencia de su aprendiz aunque mentor_id esté vacío")
    void elMentorVigenteAbreLaEvidencia() throws Exception {
        UUID mentor = mentor("Ricardo Palomino");
        asignar(grupoDeAna, mentor, "MENTOR", "now() - interval '9 days'", null);

        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(mentor)).statusCode()).isEqualTo(200);
        assertThat(pedir("/api/v1/evidence/" + fotoDeAna + "/url", sesionDe(mentor)).statusCode()).isEqualTo(200);
        assertThat(pedir("/api/v1/evidence/" + textoDeAna + "/url", sesionDe(mentor)).statusCode())
                .as("el texto no tiene archivo: 204").isEqualTo(204);
    }

    @Test
    @DisplayName("la guía de la recepción que acompaña hoy también la abre")
    void laGuiaVigenteAbreLaEvidencia() throws Exception {
        UUID guia = usuario("GUIA_APRENDIZ", "Gisela Guía");
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbcTemplate, guia, "GUIA_APRENDIZ");
        asignar(grupoDeAna, guia, "GUIA", "now() - interval '9 days'", null);

        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(guia)).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("un mentor de OTRO grupo recibe 403 en el detalle y en la URL")
    void elMentorDeOtroGrupoRecibe403() throws Exception {
        UUID mentorDeAna = mentor("Ricardo Palomino");
        asignar(grupoDeAna, mentorDeAna, "MENTOR", "now() - interval '9 days'", null);
        UUID otroGrupo = grupo("Otro 167");
        UUID otroMentor = mentor("Otro Mentor");
        asignar(otroGrupo, otroMentor, "MENTOR", "now() - interval '9 days'", null);

        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(otroMentor)).statusCode()).isEqualTo(403);
        assertThat(pedir("/api/v1/evidence/" + fotoDeAna + "/url", sesionDe(otroMentor)).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("un mentor que ROTÓ (asignación cerrada) recibe 403 aunque participantes_programa.mentor_id todavía lo nombre")
    void elMentorRotadoRecibe403() throws Exception {
        UUID rotado = mentor("Mentor Anterior");
        asignar(grupoDeAna, rotado, "MENTOR", "now() - interval '9 days'", "now() - interval '2 days'");
        jdbcTemplate.update("UPDATE renaser.participantes_programa SET mentor_id = ? WHERE usuario_id = ?", rotado, ana);
        UUID actual = mentor("Mentor Actual");
        asignar(grupoDeAna, actual, "MENTOR", "now() - interval '2 days'", null);

        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(rotado)).statusCode()).isEqualTo(403);
        assertThat(pedir("/api/v1/evidence/" + fotoDeAna + "/url", sesionDe(rotado)).statusCode()).isEqualTo(403);
        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(actual)).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("el dueño, el líder de mentores y el admin siguen abriéndola como antes")
    void duenoLiderYAdminSiguenIgual() throws Exception {
        UUID lider = usuario("LIDER_MENTORES", "Lía Líder");
        UUID admin = usuario("ADMIN", "Kelin Rojas");

        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(ana)).statusCode()).isEqualTo(200);
        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(lider)).statusCode()).isEqualTo(200);
        assertThat(pedir("/api/v1/evidence/" + fotoDeAna, sesionDe(admin)).statusCode()).isEqualTo(200);
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

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

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbcTemplate, id, rol);
        usuarios.add(id);
        return id;
    }

    private UUID mentor(String nombre) {
        UUID id = usuario("MENTOR", nombre);
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", id);
        return id;
    }

    private UUID grupo(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 10, CURRENT_DATE + 20)
                """, id, nombre, cohorte);
        grupos.add(id);
        return id;
    }

    private void asignar(UUID grupo, UUID usuario, String funcion, String inicio, String fin) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, fin, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), %s, %s, 'ADMINISTRATIVO', ?)
                """.formatted(inicio, fin == null ? "NULL" : fin), id, grupo, usuario, funcion, "prueba|" + id);
    }

    private UUID rocaDeHoy(UUID participante) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje)
                VALUES (?, ?, CURRENT_DATE, 1, 'Caminar', CAST('VERDE' AS renaser.color_pareto), 5,
                        CAST('CUERPO' AS renaser.eje_objetivo))
                """, id, participante);
        return id;
    }

    private UUID evidencia(UUID participante, UUID roca, String tipo, String ruta, String texto) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.evidencias (id, participante_id, roca_diaria_id, tipo, bucket, ruta_storage,
                                                contenido_texto, estado_validacion)
                VALUES (?, ?, ?, CAST(? AS renaser.tipo_evidencia), ?, ?, ?, 'VALIDA')
                """, id, participante, roca, tipo, ruta == null ? null : "renaser-files", ruta, texto);
        return id;
    }
}

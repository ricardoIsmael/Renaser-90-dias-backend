package com.renaser.os.users.infrastructure.adapter.in.web.security;

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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SUS-04 de punta a punta (E-366, D-214): una cuenta SUSPENDIDA en la base, con la sesión todavía viva,
 * recibe 403 en todo lo que no sea reclamar su suspensión. Sesión de verdad por {@code X-Auth-Token},
 * Spring Security, el interceptor de permisos y Postgres real.
 *
 * <p>Es el escenario exacto del e2e del 2026-09-27: el mentor se suspende con un {@code UPDATE} directo, sin
 * pasar por {@code PATCH /admin/staff/{id}/status} (que además corta las sesiones), así que el token sigue
 * sirviendo. Antes de suspenderlo, cada pedido se hace con la cuenta ACTIVA y tiene que dar 200: si no, la
 * semilla está mal y el 403 de después no probaría nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CuentaSuspendidaConSesionVivaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<UUID> usuarios = new ArrayList<>();

    private UUID cohorte;
    private UUID grupo;
    private UUID chatDelGrupo;
    private UUID mentor;
    private UUID alumna;

    @BeforeEach
    void sembrar() {
        cohorte = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte SUS-04', CURRENT_DATE - 20)",
                cohorte);
        mentor = usuario("MENTOR", "Mentor Suspendido IT");
        jdbcTemplate.update("INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (?)", mentor);
        alumna = usuario("APRENDIZ", "Alumna IT");
        LocalDate hoyEnLima = LocalDate.now(LIMA);
        // Coherente (regla 03): Día 12 = 11 días transcurridos desde fecha_inicio.
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone)
                VALUES (?, ?, 12, now() - interval '12 days', 'America/Lima')
                """, alumna, hoyEnLima.minusDays(11));
        grupo = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, mentor_id)
                VALUES (?, 'Grupo SUS-04', ?, CAST('REGULAR' AS renaser.tipo_celula), CURRENT_DATE - 5, CURRENT_DATE + 20, ?)
                """, grupo, cohorte, mentor);
        asignar(mentor, "MENTOR");
        asignar(alumna, "APRENDIZ");
        chatDelGrupo = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO renaser.conversaciones (id, tipo, celula_id, nombre) VALUES (?, 'CELULA', ?, 'Grupo SUS-04')",
                chatDelGrupo, grupo);
        for (UUID participante : List.of(mentor, alumna)) {
            jdbcTemplate.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                    chatDelGrupo, participante);
        }
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.conversaciones WHERE id = ?", chatDelGrupo);
        jdbcTemplate.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", grupo);
        jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", grupo);
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("E-366: un mentor suspendido con la sesión viva ya no lee nada de su alumna (las seis de SUS-04)")
    void unMentorSuspendidoNoLeeNadaDeSuAlumna() throws Exception {
        String sesion = sesionDe(mentor);
        Map<String, String> rutas = rutasDeSus04();

        for (Map.Entry<String, String> ruta : rutas.entrySet()) {
            assertThat(get(ruta.getValue(), sesion).statusCode())
                    .as("control con la cuenta ACTIVA: %s tiene que responder 200", ruta.getKey())
                    .isEqualTo(200);
        }

        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", mentor);

        for (Map.Entry<String, String> ruta : rutas.entrySet()) {
            HttpResponse<String> respuesta = get(ruta.getValue(), sesion);
            assertThat(respuesta.statusCode()).as("%s con la cuenta SUSPENDIDA", ruta.getKey()).isEqualTo(403);
            assertThat(respuesta.body()).as(ruta.getKey()).doesNotContain(alumna.toString());
        }
    }

    /** SUS-05 (P2): a un ADMIN suspendido se le decía «Solo ADMIN/ALCHEMIST administran este panel». */
    @Test
    @DisplayName("E-366: un ADMIN suspendido con la sesión viva recibe 403 y el motivo es la suspensión, no el rol")
    void unAdminSuspendidoRecibeElMotivoCorrecto() throws Exception {
        UUID admin = usuario("ADMIN", "Admin Suspendido IT");
        String sesion = sesionDe(admin);
        assertThat(get("/api/v1/admin/trainees", sesion).statusCode()).as("control activo").isEqualTo(200);

        jdbcTemplate.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", admin);

        HttpResponse<String> respuesta = get("/api/v1/admin/trainees", sesion);
        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.body()).contains("Cuenta suspendida");
    }

    private Map<String, String> rutasDeSus04() {
        String mes = YearMonth.now(LIMA).toString();
        Map<String, String> rutas = new LinkedHashMap<>();
        rutas.put("hábitos de la alumna", "/api/v1/mentor/groups/" + grupo + "/learners/" + alumna + "/habits");
        rutas.put("Código Renaser de la alumna", "/api/v1/mentor/groups/" + grupo + "/learners/" + alumna + "/radar");
        rutas.put("contexto del mentor", "/api/v1/mentor/context");
        rutas.put("su evaluación", "/api/v1/mentor/me/evaluation?month=" + mes);
        rutas.put("presencia del chat del grupo", "/api/v1/chat/conversations/" + chatDelGrupo + "/presence");
        rutas.put("ranking de grupos", "/api/v1/ranking/groups?cohortId=" + cohorte + "&month=" + mes);
        return rutas;
    }

    // ── Pedido y semilla ────────────────────────────────────────────────────

    private HttpResponse<String> get(String ruta, String sesion) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                        .header("X-Auth-Token", sesion).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private UUID usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", nombre, rol);
        usuarios.add(id);
        return id;
    }

    private void asignar(UUID usuario, String funcion) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.asignaciones_celula (id, celula_id, usuario_id, funcion, inicio, motivo, clave_operacion)
                VALUES (?, ?, ?, CAST(? AS renaser.funcion_acompanamiento), now() - interval '1 hour', 'ADMINISTRATIVO', ?)
                """, id, grupo, usuario, funcion, "prueba|" + id);
    }

    /** Como la deja el login real ({@code SesionWebAdapter}): el id del usuario como nombre. */
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
}

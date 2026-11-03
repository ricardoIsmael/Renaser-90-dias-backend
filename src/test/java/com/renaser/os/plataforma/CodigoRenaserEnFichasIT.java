package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Código Renaser en las fichas» (spec 034, D-299) de punta a punta: Tomcat real, sesión real, el filtro de permisos y las
 * migraciones. Lo que solo se ve así: que de fábrica el mentor, la guía y el líder reciban 404 en las TRES lecturas y
 * Administración, Alquimista y la propia persona no; que Administración lo cambie y rija en el acto; que encender devuelva
 * TODOS los registros (también los hechos mientras estuvo apagado); que cada cambio deje su fila en la auditoría append-only;
 * que no se avise a nadie de las fichas; y que Soporte TI lo decida desde su consola.
 *
 * <p>Semilla: «Fénix» con su mentora y Ana (aprendiz), «Aurora» con su guía y Beto, un líder, Administración, Alquimista,
 * Soporte TI y una administradora suspendida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CodigoRenaserEnFichasIT {

    private static final String RUTA = "/api/v1/admin/codigo-renaser-en-fichas";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeEstados copia;

    private PedidosDePrueba pedidos;
    private final List<UUID> grupos = new ArrayList<>();
    private final Map<UUID, Integer> horasAtrasDe = new java.util.HashMap<>();
    private final Instant inicio = Instant.now();
    private UUID cohorte;
    private UUID admin;
    private UUID alquimista;
    private UUID soporte;
    private UUID lider;
    private UUID mentora;
    private UUID guia;
    private UUID ana;
    private UUID beto;
    private UUID fenix;
    private UUID aurora;

    @BeforeEach
    void sembrar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'radar.fichas'");
        copia.recargar();
        admin = pedidos.usuario("ADMIN");
        alquimista = pedidos.usuario("ALQUIMISTA");
        soporte = pedidos.usuario("SOPORTE_TI");
        lider = pedidos.usuario("LIDER_MENTORES");
        mentora = pedidos.usuario("MENTOR");
        guia = pedidos.usuario("GUIA_APRENDIZ");
        ana = pedidos.usuario("APRENDIZ");
        beto = pedidos.usuario("APRENDIZ");
        programa(ana);
        programa(beto);
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, ?, CURRENT_DATE - 60)", cohorte,
                "Cohorte " + cohorte);
        fenix = grupo("Fénix " + cohorte, mentora);
        asignar(fenix, mentora, "MENTOR");
        asignar(fenix, ana, "APRENDIZ");
        aurora = grupo("Aurora " + cohorte, guia);
        asignar(aurora, guia, "MENTOR");
        asignar(aurora, beto, "APRENDIZ");
        registro(ana, "Trabajando");
        registro(beto, "Descansando");
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'radar.fichas'");
        copia.recargar();
        grupos.forEach(id -> {
            jdbc.update("UPDATE renaser.celulas SET mentor_id = NULL WHERE id = ?", id);
            jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id);
        });
        pedidos.borrarCuentas();
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    // ── de fábrica: apagado ─────────────────────────────────────────────────

    @Test
    @DisplayName("de fábrica el mentor, la guía y el líder reciben 404 «no disponible» en las tres lecturas; Administración, Alquimista y la propia persona, 200")
    void deFabricaEstaApagado() throws Exception {
        for (HttpResponse<String> r : List.of(
                leer(mentora, "/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/radar"),
                leer(guia, "/api/v1/mentor/groups/" + aurora + "/learners/" + beto + "/radar"),
                leer(lider, "/api/v1/leadership/groups/" + fenix + "/people/" + ana + "/radar"),
                leer(lider, "/api/v1/leadership/people/" + ana + "/radar"))) {
            assertThat(r.statusCode()).as(r.uri().getPath()).isEqualTo(404);
            assertThat(pedidos.json(r).get("codigo").asText()).isEqualTo("FUNCIONALIDAD_NO_DISPONIBLE");
            assertThat(r.body()).as("sin un solo dato del Código Renaser").doesNotContain("Trabajando", "Descansando");
        }
        for (UUID quien : List.of(admin, alquimista)) {
            assertThat(leer(quien, "/api/v1/leadership/groups/" + fenix + "/people/" + ana + "/radar").statusCode())
                    .isEqualTo(200);
            assertThat(leer(quien, "/api/v1/leadership/people/" + ana + "/radar").statusCode()).isEqualTo(200);
        }
        assertThat(leer(ana, "/api/v1/radar/history").statusCode()).as("la propia persona").isEqualTo(200);
        assertThat(clavesNoDisponibles(mentora)).contains("radar.fichas");
        assertThat(clavesNoDisponibles(lider)).contains("radar.fichas");
        assertThat(clavesNoDisponibles(admin)).doesNotContain("radar.fichas");
        assertThat(clavesNoDisponibles(ana)).doesNotContain("radar.fichas");
    }

    // ── Administración lo cambia ────────────────────────────────────────────

    @Test
    @DisplayName("Administración lee «apagado», lo enciende y rige en el acto (con TODOS los registros); lo apaga de nuevo y vuelve el 404; no se pierde ningún dato")
    void administracionLoEnciendeYApaga() throws Exception {
        JsonNode inicial = pedidos.json(pedidos.enviar("GET", RUTA, pedidos.sesionDe(admin), null));
        assertThat(inicial.get("visible").asBoolean()).isFalse();
        assertThat(inicial.get("ultimoCambio").isNull()).isTrue();
        long registrosAntes = registros(ana);
        registro(ana, "Hecho mientras estaba apagado");

        HttpResponse<String> encender = pedidos.enviar("PUT", RUTA, pedidos.sesionDe(admin), Map.of("visible", true));

        assertThat(encender.statusCode()).isEqualTo(200);
        assertThat(encender.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode encendido = pedidos.json(encender);
        assertThat(encendido.get("visible").asBoolean()).isTrue();
        assertThat(encendido.get("ultimoCambio").get("porId").asText()).isEqualTo(admin.toString());
        for (HttpResponse<String> r : List.of(
                leer(mentora, "/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/radar"),
                leer(lider, "/api/v1/leadership/groups/" + fenix + "/people/" + ana + "/radar"),
                leer(lider, "/api/v1/leadership/people/" + ana + "/radar"))) {
            assertThat(r.statusCode()).as(r.uri().getPath()).isEqualTo(200);
            assertThat(r.body()).contains("Trabajando", "Hecho mientras estaba apagado");
        }
        assertThat(leer(guia, "/api/v1/mentor/groups/" + aurora + "/learners/" + beto + "/radar").statusCode()).isEqualTo(200);
        assertThat(clavesNoDisponibles(mentora)).doesNotContain("radar.fichas");

        HttpResponse<String> apagar = pedidos.enviar("PUT", RUTA, pedidos.sesionDe(alquimista), Map.of("visible", false));

        assertThat(pedidos.json(apagar).get("visible").asBoolean()).isFalse();
        assertThat(pedidos.json(apagar).get("ultimoCambio").get("porId").asText()).isEqualTo(alquimista.toString());
        assertThat(leer(mentora, "/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/radar").statusCode()).isEqualTo(404);
        assertThat(leer(lider, "/api/v1/leadership/people/" + ana + "/radar").statusCode()).isEqualTo(404);
        assertThat(leer(admin, "/api/v1/leadership/people/" + ana + "/radar").statusCode()).isEqualTo(200);
        assertThat(registros(ana)).as("apagar y encender no toca ningún dato").isEqualTo(registrosAntes + 1);
    }

    @Test
    @DisplayName("cada cambio deja su fila en la auditoría append-only (quién, cuándo, antes y después); pedir lo que ya rige no deja otra; y no se avisa a la persona, al mentor ni al líder")
    void auditoriaYSinAvisos() throws Exception {
        String sesion = pedidos.sesionDe(admin);
        pedidos.enviar("PUT", RUTA, sesion, Map.of("visible", true));
        pedidos.enviar("PUT", RUTA, sesion, Map.of("visible", true));
        pedidos.enviar("PUT", RUTA, sesion, Map.of("visible", false));
        pedidos.esperarElOutbox(inicio);

        List<Map<String, Object>> filas = jdbc.queryForList("SELECT actor_id, tipo, motivo, "
                + "(antes -> 'apagadaPara')::text AS antes, (despues -> 'apagadaPara')::text AS despues "
                + "FROM renaser.cambios_auditados WHERE clave = 'radar.fichas' AND actor_id = ? ORDER BY creado_en", admin);
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("actor_id", admin).containsEntry("tipo", "MANUAL");
        assertThat(filas.get(0).get("motivo")).isEqualTo("Cambio desde Administración (Código Renaser en las fichas)");
        assertThat(filas.get(0).get("antes")).as("de fábrica: apagado para los tres")
                .isEqualTo("[\"MENTOR\", \"MENTOR_LEAD\", \"TRAINEE_GUIDE\"]");
        assertThat(filas.get(0).get("despues")).isEqualTo("[]");
        assertThat(filas.get(1).get("antes")).isEqualTo("[]");
        assertThat(filas.get(1).get("despues")).isEqualTo("[\"MENTOR\", \"MENTOR_LEAD\", \"TRAINEE_GUIDE\"]");

        assertThatThrownBy(() -> jdbc.update("UPDATE renaser.cambios_auditados SET motivo = 'x' WHERE actor_id = ?", admin))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM renaser.cambios_auditados WHERE actor_id = ?", admin))
                .isInstanceOf(DataAccessException.class);

        for (UUID persona : List.of(ana, beto, mentora, guia, lider)) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ?", Integer.class,
                    persona)).as("no se avisa a nadie de las fichas").isZero();
        }
    }

    // ── quién puede ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("el aprendiz, el mentor, la guía y el líder reciben 403 al leer y al cambiar; una administradora suspendida también, con la sesión viva; nada cambia")
    void sinPermisoOSuspendida() throws Exception {
        UUID suspendida = pedidos.usuario("ADMIN", "SUSPENDIDO");
        String sesionDeLaSuspendida = pedidos.sesionDe(suspendida);

        for (UUID quien : List.of(ana, mentora, guia, lider)) {
            String sesion = pedidos.sesionDe(quien);
            assertThat(pedidos.enviar("GET", RUTA, sesion, null).statusCode()).as("GET " + quien).isEqualTo(403);
            assertThat(pedidos.enviar("PUT", RUTA, sesion, Map.of("visible", true)).statusCode()).as("PUT " + quien).isEqualTo(403);
        }
        assertThat(pedidos.enviar("GET", RUTA, sesionDeLaSuspendida, null).statusCode()).isEqualTo(403);
        assertThat(pedidos.enviar("PUT", RUTA, sesionDeLaSuspendida, Map.of("visible", true)).statusCode()).isEqualTo(403);
        assertThat(pedidos.enviar("PUT", RUTA, pedidos.sesionDe(admin), Map.of()).statusCode()).as("sin «visible»").isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_estado WHERE clave = 'radar.fichas'",
                Integer.class)).isZero();
        assertThat(leer(mentora, "/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/radar").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("el rol no se inyecta: mandar «role» en el cuerpo no le da permiso a un mentor")
    void elRolNoSeInyecta() throws Exception {
        HttpResponse<String> r = pedidos.enviar("PUT", RUTA, pedidos.sesionDe(mentora),
                Map.of("visible", true, "role", "ADMIN"));

        assertThat(r.statusCode()).isEqualTo(403);
    }

    // ── Soporte TI ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Soporte TI lo decide por el mismo endpoint; y desde su consola, rol por rol: un estado mixto se lee «no lo ven todos» y Administración lo iguala")
    void soporteTi() throws Exception {
        String sesion = pedidos.sesionDe(soporte);
        assertThat(pedidos.json(pedidos.enviar("PUT", RUTA, sesion, Map.of("visible", true))).get("visible").asBoolean()).isTrue();
        assertThat(leer(mentora, "/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/radar").statusCode()).isEqualTo(200);

        String consola = pedidos.desbloquear(sesion);
        long version = versionDeLaFila();
        HttpResponse<String> cambio = pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/radar.fichas", sesion,
                Map.of("prendidaGlobal", true, "porRol", Map.of("MENTOR", false), "motivo", "Prueba de mixto",
                        "version", version),
                Map.of("X-Consola-Token", consola));
        assertThat(cambio.statusCode()).isEqualTo(200);

        JsonNode mixto = pedidos.json(pedidos.enviar("GET", RUTA, pedidos.sesionDe(admin), null));
        assertThat(mixto.get("visible").asBoolean()).isFalse();
        assertThat(mixto.get("mixto").asBoolean()).isTrue();
        assertThat(mixto.get("porRol").get("MENTOR").asBoolean()).isFalse();
        assertThat(mixto.get("porRol").get("MENTOR_LEAD").asBoolean()).isTrue();
        assertThat(leer(mentora, "/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/radar").statusCode()).isEqualTo(404);
        assertThat(leer(lider, "/api/v1/leadership/people/" + ana + "/radar").statusCode()).isEqualTo(200);

        JsonNode igualado = pedidos.json(pedidos.enviar("PUT", RUTA, pedidos.sesionDe(admin), Map.of("visible", true)));
        assertThat(igualado.get("visible").asBoolean()).isTrue();
        assertThat(igualado.get("mixto").asBoolean()).isFalse();
        assertThat(leer(mentora, "/api/v1/mentor/groups/" + fenix + "/learners/" + ana + "/radar").statusCode()).isEqualTo(200);
    }

    // ── Semilla y ayudas ────────────────────────────────────────────────────

    private long versionDeLaFila() {
        return jdbc.queryForObject("SELECT version FROM renaser.funcionalidades_estado WHERE clave = 'radar.fichas'",
                Long.class);
    }

    private HttpResponse<String> leer(UUID quien, String ruta) throws Exception {
        return pedidos.enviar("GET", ruta, pedidos.sesionDe(quien), null);
    }

    private List<String> clavesNoDisponibles(UUID quien) throws Exception {
        return textos(pedidos.json(leer(quien, "/api/v1/funcionalidades/mias")).get("noDisponibles"));
    }

    private static List<String> textos(JsonNode arreglo) {
        List<String> claves = new ArrayList<>();
        arreglo.forEach(n -> claves.add(n.asText()));
        return claves;
    }

    private long registros(UUID persona) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.registros_radar WHERE participante_id = ?", Long.class,
                persona);
    }

    /** Un registro por hora y por persona (V52): cada uno con una hora distinta, hacia atrás. */
    private void registro(UUID persona, String queHago) {
        int horasAtras = horasAtrasDe.merge(persona, 1, Integer::sum);
        jdbc.update("INSERT INTO renaser.registros_radar (participante_id, que_hago, que_pienso, que_siento, "
                + "nivel_energia, que_evito, creado_en) VALUES (?, ?, 'Pensando', 'Tranquilo', 7, 'Nada', "
                + "now() - make_interval(hours => ?))", persona, queHago, horasAtras);
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

package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.SesionDeSoloLectura;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Ver como rol» de punta a punta (spec 002, fase 2, US5; contrato fase-2.md §5 y §7; D-270), contra el Tomcat, el
 * Postgres y el Redis reales: Soporte TI abre «ver como Mentor», recibe el token de una sesión NUEVA (la suya sigue
 * sirviendo), lee como mentor, toda escritura responde 403 {@code MODO_SOLO_LECTURA} (REST y el SEND de STOMP), sale
 * con {@code DELETE /ver-como} y el token deja de servir; la auditoría queda con la forma que lee la app. Además: las
 * cuentas de prueba no aparecen en rankings, padrones, semáforos, listas de Administración, la consola ni los conteos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class VerComoDePuntaAPuntaIT {

    private static final String RUTA = "/api/v1/soporte-ti/ver-como";
    private static final String GRUPO_DE_PRUEBA = "Grupo de prueba (ver como)";
    private static final String GRUPO_DE_PRUEBA_ID = "7e57c0de-0000-4000-8000-000000000002";
    private static final String COHORTE_DE_PRUEBA_ID = "7e57c0de-0000-4000-8000-000000000001";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sesionesPorPersona;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserSummaryFinder usuarios;
    @Autowired
    private ParticipacionProgramaFinder participaciones;

    private PedidosDePrueba pedidos;
    private UUID soporteId;
    private String soporte;
    private Map<String, String> consola;
    private final List<WebSocket> sockets = new ArrayList<>();
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() throws Exception {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporteId = pedidos.usuario("SOPORTE_TI");
        soporte = pedidos.sesionDe(soporteId);
        consola = Map.of("X-Consola-Token", pedidos.desbloquear(soporte));
    }

    /**
     * Las cuentas de prueba no se borran (el correo las identifica y «ver como» las repara la próxima vez): se suspenden,
     * así ningún barrido de otra prueba las encuentra entre las activas.
     */
    @AfterEach
    void limpiar() throws Exception {
        sockets.forEach(WebSocket::abort);
        pedidos.esperarElOutbox(inicio);
        jdbc.update("UPDATE renaser.usuarios SET estado = 'SUSPENDIDO', rol_personalizado_id = NULL "
                + "WHERE es_cuenta_de_prueba");
        jdbc.update("UPDATE renaser.usuarios SET rol_personalizado_id = NULL WHERE rol_personalizado_id IS NOT NULL");
        jdbc.update("DELETE FROM renaser.roles_personalizados");
        pedidos.borrarCuentas();
    }

    private HttpResponse<String> abrir(Map<String, Object> cuerpo) throws Exception {
        return pedidos.enviar("POST", RUTA, soporte, cuerpo, consola);
    }

    /** Abre «ver como» y devuelve el token de la sesión nueva. */
    private String verComo(String rol) throws Exception {
        HttpResponse<String> abierta = abrir(Map.of("rol", rol));
        assertThat(abierta.statusCode()).as(abierta.body()).isEqualTo(200);
        return abierta.headers().firstValue("X-Auth-Token").orElseThrow();
    }

    private UUID idDeLaCuenta(String token) throws Exception {
        return UUID.fromString(pedidos.json(pedidos.enviar("GET", "/api/v1/auth/me", token, null)).get("id").asText());
    }

    private static void esSoloLectura(HttpResponse<String> respuesta, String que) {
        assertThat(respuesta.statusCode()).as(que).isEqualTo(403);
        assertThat(respuesta.body()).as(que).contains("\"codigo\":\"MODO_SOLO_LECTURA\"")
                .contains("Estás viendo la app como otro rol: no se puede cambiar nada.");
    }

    @Test
    @DisplayName("ver como Mentor: token nuevo (la sesión de Soporte TI sigue), lee como mentor, no escribe, sale y "
            + "el token deja de servir; auditado")
    void verComoMentorDePuntaAPunta() throws Exception {
        HttpResponse<String> abierta = abrir(Map.of("rol", "MENTOR"));
        assertThat(abierta.statusCode()).as(abierta.body()).isEqualTo(200);
        String token = abierta.headers().firstValue("X-Auth-Token").orElseThrow();
        assertThat(token).isNotBlank().isNotEqualTo(soporte);
        JsonNode cuerpo = pedidos.json(abierta);
        assertThat(cuerpo.get("cuenta").get("rol").asText()).isEqualTo("MENTOR");
        assertThat(cuerpo.get("cuenta").get("nombre").asText()).isEqualTo("Prueba · Mentor");
        assertThat(cuerpo.get("cuenta").get("rolPersonalizado").isNull()).isTrue();
        assertThat(Instant.parse(cuerpo.get("venceEn").asText()))
                .isBetween(Instant.now().plus(Duration.ofMinutes(29)), Instant.now().plus(Duration.ofMinutes(31)));
        assertThat(pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades", soporte, null, consola).statusCode())
                .as("la sesión de Soporte TI con la que se pidió sigue sirviendo").isEqualTo(200);

        JsonNode yo = pedidos.json(pedidos.enviar("GET", "/api/v1/auth/me", token, null));
        assertThat(yo.get("role").asText()).isEqualTo("MENTOR");
        assertThat(yo.get("id").asText()).isEqualTo(cuerpo.get("cuenta").get("id").asText());
        assertThat(pedidos.enviar("POST", "/api/v1/users/me", token, null).statusCode())
                .as("el perfil propio se lee con POST").isEqualTo(200);
        assertThat(pedidos.enviar("GET", "/api/v1/me/cells", token, null).body())
                .as("el mentor de prueba lidera su grupo").contains(GRUPO_DE_PRUEBA);

        esSoloLectura(pedidos.enviar("POST", "/api/v1/wall", token, Map.of("contenido", "hola")), "publicar");
        esSoloLectura(pedidos.enviar("PATCH", "/api/v1/users/me", token, Map.of("fullName", "Otro")), "editar perfil");
        esSoloLectura(pedidos.enviar("PUT", "/api/v1/admin/staff/" + UUID.randomUUID(), token, Map.of()), "PUT");
        esSoloLectura(pedidos.enviar("DELETE", "/api/v1/users/me/account-deletion", token, null), "DELETE");
        esSoloLectura(pedidos.enviar("POST", RUTA, token, Map.of("rol", "ADMIN"), consola), "abrir otro ver como");
        assertThat(pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades", token, null, consola).statusCode())
                .as("la sesión de solo lectura no abre la consola").isEqualTo(403);
        assertThat(sesionesPorPersona.findByPrincipalName(soporteId.toString()))
                .as("cerrar las sesiones de Soporte TI también la cierra").containsKey(token);

        assertThat(pedidos.enviar("DELETE", RUTA, token, null).statusCode()).isEqualTo(204);
        assertThat(pedidos.enviar("GET", "/api/v1/auth/me", token, null).statusCode())
                .as("el token deja de servir").isNotEqualTo(200);
        assertThat(sesiones.findById(token)).isNull();
        assertThat(pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades", soporte, null, consola).statusCode())
                .isEqualTo(200);

        List<JsonNode> filas = new ArrayList<>();
        pedidos.json(pedidos.enviar("GET", "/api/v1/soporte-ti/auditoria?actor=" + soporteId, soporte, null, consola))
                .get("contenido").forEach(fila -> {
                    if (fila.get("tipo").asText().equals("VER_COMO")) {
                        filas.add(fila);
                    }
                });
        assertThat(filas).hasSize(2);
        JsonNode salida = filas.get(0);
        JsonNode entrada = filas.get(1);
        assertThat(entrada.get("antes").isNull()).isTrue();
        assertThat(entrada.get("despues").get("rol").asText()).isEqualTo("MENTOR");
        assertThat(salida.get("antes").get("rol").asText()).isEqualTo("MENTOR");
        assertThat(salida.get("despues").isNull()).isTrue();
        assertThat(entrada.get("actor").get("id").asText()).isEqualTo(soporteId.toString());
    }

    @Test
    @DisplayName("el SEND de STOMP de una sesión de solo lectura recibe ERROR MODO_SOLO_LECTURA; la voz en vivo no abre")
    void websocketsDeSoloLectura() throws Exception {
        String token = verComo("TRAINEE");
        Cliente cliente = new Cliente();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder().header("X-Auth-Token", token)
                .buildAsync(URI.create("ws://localhost:" + puerto + "/ws"), cliente).get(10, TimeUnit.SECONDS);
        sockets.add(socket);
        enviar(socket, "CONNECT\naccept-version:1.2\nhost:renaser\nheart-beat:0,0\n\n\u0000");
        cliente.esperar("CONNECTED");

        enviar(socket, "SEND\ndestination:/app/conversaciones/" + UUID.randomUUID() + "/mensajes\n\nhola\u0000");

        assertThat(cliente.esperar("ERROR")).contains("MODO_SOLO_LECTURA");

        assertThatThrownBy(() -> HttpClient.newHttpClient().newWebSocketBuilder().header("X-Auth-Token", token)
                .buildAsync(URI.create("ws://localhost:" + puerto + "/api/v1/renasia/voz/en-vivo"), new Cliente())
                .join())
                .isInstanceOf(CompletionException.class)
                .satisfies(error -> assertThat(((WebSocketHandshakeException) error.getCause()).getResponse()
                        .statusCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("pasada la media hora, el token responde 401 VER_COMO_VENCIDO y la sesión se cierra")
    void venceALaMediaHora() throws Exception {
        String token = verComo("ADMIN");
        vencer(sesiones, token);

        HttpResponse<String> vencida = pedidos.enviar("GET", "/api/v1/auth/me", token, null);

        assertThat(vencida.statusCode()).isEqualTo(401);
        assertThat(vencida.body()).contains("\"codigo\":\"VER_COMO_VENCIDO\"");
        assertThat(sesiones.findById(token)).isNull();
    }

    private static <S extends Session> void vencer(SessionRepository<S> repositorio, String token) {
        S sesion = repositorio.findById(token);
        sesion.setAttribute(SesionDeSoloLectura.VENCE_EN, Instant.now().minusSeconds(1).toEpochMilli());
        repositorio.save(sesion);
    }

    @Test
    @DisplayName("las cuentas de prueba no aparecen en rankings, padrones, semáforos, listas de Administración, la "
            + "consola, los destinatarios por rol ni los conteos")
    void lasCuentasDePruebaNoAparecen() throws Exception {
        String admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        String lider = pedidos.sesionDe(pedidos.usuario("LIDER_MENTORES"));
        JsonNode panelAntes = pedidos.json(pedidos.enviar("GET", "/api/v1/admin/onboarding/dashboard", admin, null));

        UUID aprendiz = idDeLaCuenta(verComo("TRAINEE"));
        UUID mentor = idDeLaCuenta(verComo("MENTOR"));
        UUID guia = idDeLaCuenta(verComo("TRAINEE_GUIDE"));
        UUID liderDePrueba = idDeLaCuenta(verComo("MENTOR_LEAD"));
        UUID adminDePrueba = idDeLaCuenta(verComo("ADMIN"));
        UUID alquimista = idDeLaCuenta(verComo("ALCHEMIST"));
        Set<String> todas = Set.of(aprendiz.toString(), mentor.toString(), guia.toString(), liderDePrueba.toString(),
                adminDePrueba.toString(), alquimista.toString());

        noAparece(pedidos.enviar("GET", "/api/v1/admin/trainees?q=Prueba&size=100", admin, null), todas,
                "Personas (padrón de Administración)");
        noAparece(pedidos.enviar("GET", "/api/v1/admin/staff?size=200", admin, null), todas, "Equipo");
        noAparece(pedidos.enviar("GET", "/api/v1/admin/cells/mentores", admin, null), todas, "mentores de un grupo");
        noAparece(pedidos.enviar("GET", "/api/v1/admin/cells/aprendices-disponibles", admin, null), todas,
                "aprendices para un grupo");
        noAparece(pedidos.enviar("GET", "/api/v1/admin/cells/dashboard", admin, null),
                Set.of(GRUPO_DE_PRUEBA_ID, GRUPO_DE_PRUEBA), "panel de grupos");
        noAparece(pedidos.enviar("GET", "/api/v1/admin/cohorts", admin, null), Set.of(COHORTE_DE_PRUEBA_ID),
                "cohortes");
        noAparece(pedidos.enviar("GET", "/api/v1/admin/caja", admin, null), todas, "Caja Renaser");
        noAparece(pedidos.enviar("GET", "/api/v1/admin/semaforo/atencion", admin, null), todas, "atención semáforo");
        noAparece(pedidos.enviar("GET", "/api/v1/semaforo/groups", lider, null),
                Set.of(GRUPO_DE_PRUEBA_ID, GRUPO_DE_PRUEBA), "semáforo por grupos");
        noAparece(pedidos.enviar("GET", "/api/v1/leadership/mentors", lider, null), todas, "padrón de mentores");
        noAparece(pedidos.enviar("GET", "/api/v1/soporte-ti/personas?q=prueba", soporte, null, consola), todas,
                "personas de la consola");

        assertThat(pedidos.enviar("POST", "/api/v1/admin/ranking/snapshots", admin, null).statusCode()).isEqualTo(200);
        for (String tipo : List.of("GENERAL", "LEAGUE", "KILOMETROS")) {
            noAparece(pedidos.enviar("GET", "/api/v1/ranking/" + tipo, admin, null), todas, "ranking " + tipo);
        }

        assertThat(usuarios.aprendicesActivos()).extracting(UserSummary::id).doesNotContain(UserId.of(aprendiz));
        assertThat(participaciones.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST,
                UserRole.MENTOR_LEAD, UserRole.MENTOR, UserRole.TRAINEE_GUIDE, UserRole.TRAINEE)))
                .as("destinatarios de avisos por rol y staff de los chats de soporte")
                .doesNotContain(UserId.of(aprendiz), UserId.of(mentor), UserId.of(guia), UserId.of(liderDePrueba),
                        UserId.of(adminDePrueba), UserId.of(alquimista));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ANY (?)",
                Integer.class, (Object) todas.stream().map(UUID::fromString).toArray(UUID[]::new)))
                .as("ningún aviso a una cuenta de prueba").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.participantes_conversacion p "
                        + "JOIN renaser.conversaciones c ON c.id = p.conversacion_id "
                        + "WHERE p.usuario_id = ANY (?) AND c.tipo IN ('GLOBAL', 'SOPORTE')", Integer.class,
                (Object) todas.stream().map(UUID::fromString).toArray(UUID[]::new)))
                .as("ni en el chat de la comunidad ni en un chat de soporte").isZero();

        JsonNode panelDespues = pedidos.json(pedidos.enviar("GET", "/api/v1/admin/onboarding/dashboard", admin, null));
        assertThat(panelDespues.get("totalActiveTrainees").asLong())
                .isEqualTo(panelAntes.get("totalActiveTrainees").asLong());
        assertThat(panelDespues.get("totalOnboardingCompleted").asLong())
                .isEqualTo(panelAntes.get("totalOnboardingCompleted").asLong());
    }

    private static void noAparece(HttpResponse<String> respuesta, Set<String> textos, String donde) {
        assertThat(respuesta.statusCode()).as(donde + ": " + respuesta.body()).isEqualTo(200);
        for (String texto : textos) {
            assertThat(respuesta.body()).as(donde).doesNotContain(texto);
        }
    }

    @Test
    @DisplayName("ver como un rol personalizado: su restricción rige en la sesión; la cuenta de prueba no cuenta entre "
            + "sus personas ni impide borrarlo")
    void verComoRolPersonalizado() throws Exception {
        Map<String, Object> rol = new LinkedHashMap<>();
        rol.put("nombre", "Mentor junior");
        rol.put("rolBase", "MENTOR");
        rol.put("funcionalidades", todoLoDelMentorMenosEventos());
        rol.put("motivo", "Probar ver como");
        HttpResponse<String> creado = pedidos.enviar("POST", "/api/v1/soporte-ti/roles", soporte, rol, consola);
        assertThat(creado.statusCode()).as(creado.body()).isEqualTo(201);
        String rolId = pedidos.json(creado).get("id").asText();

        HttpResponse<String> abierta = abrir(Map.of("rolPersonalizadoId", rolId));
        assertThat(abierta.statusCode()).as(abierta.body()).isEqualTo(200);
        String token = abierta.headers().firstValue("X-Auth-Token").orElseThrow();
        JsonNode cuenta = pedidos.json(abierta).get("cuenta");
        assertThat(cuenta.get("rol").asText()).isEqualTo("MENTOR");
        assertThat(cuenta.get("rolPersonalizado").get("id").asText()).isEqualTo(rolId);
        assertThat(cuenta.get("rolPersonalizado").get("nombre").asText()).isEqualTo("Mentor junior");
        assertThat(esperarNoDisponible(token, "eventos")).as("la regla del rol personalizado la restringe").isTrue();

        assertThat(pedidos.json(pedidos.enviar("GET", "/api/v1/soporte-ti/roles", soporte, null, consola)).get(0)
                .get("personas").asLong()).as("la cuenta de prueba no es una persona del rol").isZero();
        Map<String, String> critico = Map.of("X-Consola-Token", consola.get("X-Consola-Token"), "X-Reautenticacion",
                pedidos.reautenticar(soporte, consola.get("X-Consola-Token")));
        assertThat(pedidos.enviar("DELETE", "/api/v1/soporte-ti/roles/" + rolId + "?motivo=Fin", soporte, null,
                critico).statusCode()).as("y no impide borrarlo").isEqualTo(204);

        assertThat(pedidos.enviar("DELETE", RUTA, token, null).statusCode()).isEqualTo(204);
        List<JsonNode> filas = new ArrayList<>();
        pedidos.json(pedidos.enviar("GET", "/api/v1/soporte-ti/auditoria?actor=" + soporteId, soporte, null, consola))
                .get("contenido").forEach(fila -> {
                    if (fila.get("tipo").asText().equals("VER_COMO")) {
                        filas.add(fila);
                    }
                });
        assertThat(filas.getLast().get("despues").get("rolPersonalizado").get("nombre").asText())
                .isEqualTo("Mentor junior");
        assertThat(filas.getFirst().get("antes").get("rolPersonalizado").get("id").asText()).isEqualTo(rolId);
    }

    private boolean esperarNoDisponible(String token, String clave) throws Exception {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite) {
            JsonNode mias = pedidos.json(pedidos.enviar("GET", "/api/v1/funcionalidades/mias", token, null));
            for (JsonNode noDisponible : mias.get("noDisponibles")) {
                if (noDisponible.asText().equals(clave)) {
                    return true;
                }
            }
            Thread.sleep(200);
        }
        return false;
    }

    private List<String> todoLoDelMentorMenosEventos() throws Exception {
        List<String> claves = new ArrayList<>();
        for (JsonNode funcionalidad : pedidos.json(pedidos.enviar("GET", "/api/v1/soporte-ti/funcionalidades",
                soporte, null, consola))) {
            boolean laUsaMentor = false;
            for (JsonNode rol : funcionalidad.get("rolesQueLaUsan")) {
                laUsaMentor |= rol.asText().equals("MENTOR");
            }
            String clave = funcionalidad.get("clave").asText();
            if (laUsaMentor && !clave.equals("eventos") && !clave.startsWith("eventos.")) {
                claves.add(clave);
            }
        }
        return claves;
    }

    @Test
    @DisplayName("autorización negativa: ADMIN, ALQUIMISTA, MENTOR, GUÍA, LÍDER, APRENDIZ y Soporte TI suspendido → "
            + "403; sin desbloqueo → 423; salir sin una sesión «ver como» → 404")
    void autorizacionNegativa() throws Exception {
        for (String rol : List.of("ADMIN", "ALQUIMISTA", "MENTOR", "GUIA_APRENDIZ", "LIDER_MENTORES", "APRENDIZ")) {
            String sesion = pedidos.sesionDe(pedidos.usuario(rol));
            assertThat(pedidos.enviar("POST", RUTA, sesion, Map.of("rol", "MENTOR"), consola).statusCode()).as(rol)
                    .isEqualTo(403);
        }
        String suspendido = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI", "SUSPENDIDO"));
        assertThat(pedidos.enviar("POST", RUTA, suspendido, Map.of("rol", "MENTOR"), consola).statusCode())
                .as("Soporte TI suspendido").isEqualTo(403);
        assertThat(pedidos.enviar("POST", RUTA, soporte, Map.of("rol", "MENTOR")).statusCode())
                .as("sin X-Consola-Token").isEqualTo(423);
        assertThat(abrir(Map.of("rol", "IT_SUPPORT")).statusCode()).as("Soporte TI no se mira").isEqualTo(400);
        assertThat(abrir(Map.of()).statusCode()).as("sin rol").isEqualTo(400);
        assertThat(abrir(Map.of("rolPersonalizadoId", UUID.randomUUID().toString())).statusCode())
                .as("rol personalizado que no existe").isEqualTo(404);
        assertThat(pedidos.enviar("DELETE", RUTA, soporte, null).statusCode())
                .as("salir con una sesión que no es de «ver como»").isEqualTo(404);
    }

    // ── Cliente STOMP mínimo (como LatidosDelChatIT) ───────────────────────────

    private static void enviar(WebSocket socket, String trama) {
        socket.sendBinary(ByteBuffer.wrap(trama.getBytes(StandardCharsets.UTF_8)), true).join();
    }

    private static final class Cliente implements WebSocket.Listener {
        private final BlockingQueue<String> tramas = new LinkedBlockingQueue<>();
        private final StringBuilder parcial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence datos, boolean ultimo) {
            parcial.append(datos);
            if (ultimo) {
                tramas.add(parcial.toString());
                parcial.setLength(0);
            }
            socket.request(1);
            return null;
        }

        String esperar(String comando) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < limite) {
                String trama = tramas.poll(200, TimeUnit.MILLISECONDS);
                if (trama != null && trama.startsWith(comando + "\n")) {
                    return trama;
                }
            }
            throw new AssertionError("No llegó " + comando + "; llegó: " + tramas);
        }
    }
}

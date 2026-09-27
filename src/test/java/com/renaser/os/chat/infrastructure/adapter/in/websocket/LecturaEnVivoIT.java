package com.renaser.os.chat.infrastructure.adapter.in.websocket;

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
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La doble marca de leído (D-208) de punta a punta: Tomcat real, sesión de verdad por
 * {@code X-Auth-Token}, Postgres, Redis Pub/Sub y un cliente STOMP en binario como el de la app.
 *
 * <p>Lo que las pruebas unitarias no ven: que el aviso {@code READ} recorre Redis y el broker hasta el
 * socket de quien escribió, que el listado trae {@code status} con lo que quedó en la base, y que nadie
 * de afuera lo ve. Contra el código viejo, la primera prueba no recibe ningún {@code READ} y el listado
 * no trae {@code status}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LecturaEnVivoIT {

    private static final String FIN_DE_TRAMA = "\u0000";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int puerto;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<WebSocket> abiertos = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> conversaciones = new ArrayList<>();

    private UUID ana;
    private UUID luis;
    private UUID marta;
    private UUID kelin;
    private UUID rita;
    private UUID directa;
    private UUID soporte;

    @BeforeEach
    void seed() {
        ana = usuario("APRENDIZ", "ACTIVO");
        luis = usuario("APRENDIZ", "ACTIVO");
        marta = usuario("APRENDIZ", "ACTIVO");
        kelin = usuario("ADMIN", "ACTIVO");
        rita = usuario("ADMIN", "SUSPENDIDO");
        directa = conversacion("DIRECTA", ana + "_" + luis, null);
        participa(directa, ana);
        participa(directa, luis);
        soporte = conversacion("SOPORTE", "soporte:" + ana, "Ana – Formación Renaser");
        participa(soporte, ana);
        participa(soporte, kelin);
        // Suspendida y sin haber leído nunca: con su fila adentro, como la deja suspender a alguien.
        participa(soporte, rita);
    }

    @AfterEach
    void limpiar() {
        abiertos.forEach(WebSocket::abort);
        conversaciones.forEach(id -> jdbc.update("DELETE FROM renaser.conversaciones WHERE id = ?", id));
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    @Test
    @DisplayName("1 a 1, en vivo: Luis abre el chat y a Ana le llega {event: READ, readUpTo} por su suscripción")
    void unoAUnoElAvisoLlegaEnVivo() throws Exception {
        JsonNode enviado = enviar(directa, sesionDe(ana), "hola, Luis");
        Cliente deAna = suscribir(ana, directa);
        String sesionDeLuis = sesionDe(luis);

        JsonNode aviso = JSON.readTree(hastaQueLlegue(deAna, cuerpo -> cuerpo.contains("\"event\":\"READ\""),
                () -> assertThat(marcarLeido(directa, sesionDeLuis)).isEqualTo(200)));

        // E-344: el createdAt de la respuesta de enviar es el guardado, así que se puede comparar tal cual.
        assertThat(Instant.parse(aviso.get("readUpTo").asText())).as("todos leyeron hasta")
                .isAfterOrEqualTo(Instant.parse(enviado.get("createdAt").asText()));
        assertThat(aviso.has("conversationId")).as("el canal ya la nombra").isFalse();
    }

    @Test
    @DisplayName("1 a 1, listado: el mensaje de Ana pasa de SENT a READ cuando Luis lee; para Luis no lleva marca")
    void unoAUnoElListadoPasaDeSentARead() throws Exception {
        String sesionDeAna = sesionDe(ana);
        UUID mensaje = UUID.fromString(enviar(directa, sesionDeAna, "hola, Luis").get("id").asText());
        assertThat(statusDe(listar(directa, sesionDeAna), mensaje)).as("antes de que Luis lea").contains("SENT");

        assertThat(marcarLeido(directa, sesionDe(luis))).isEqualTo(200);

        assertThat(statusDe(listar(directa, sesionDeAna), mensaje)).contains("READ");
        assertThat(statusDe(listar(directa, sesionDe(luis)), mensaje)).as("para Luis no es suyo: sin marca").isEmpty();
    }

    @Test
    @DisplayName("soporte: el ✓✓ de la aprendiz llega cuando lee el staff activo; la admin suspendida no lo frena")
    void soporteConUnaCuentaSuspendida() throws Exception {
        String sesionDeAna = sesionDe(ana);
        UUID mensaje = UUID.fromString(enviar(soporte, sesionDeAna, "necesito ayuda").get("id").asText());

        assertThat(marcarLeido(soporte, sesionDe(kelin))).isEqualTo(200);

        assertThat(statusDe(listar(soporte, sesionDeAna), mensaje)).contains("READ");
    }

    @Test
    @DisplayName("comunidad: aunque todos lean, queda SENT y no sale ningún aviso de lectura")
    void comunidad() throws Exception {
        UUID global = globalConParticipantes(ana, luis);
        String sesionDeAna = sesionDe(ana);
        Cliente deAna = suscribir(ana, global);
        // El eco de su propio mensaje prueba que la suscripción ya corre: sin eso, «no llegó ningún
        // READ» pasaría aunque el canal estuviera mudo.
        String eco = hastaQueLlegue(deAna,
                cuerpo -> cuerpo.contains("\"event\":\"MESSAGE\"") && cuerpo.contains(ana.toString()),
                () -> enviar(global, sesionDeAna, "hola, comunidad"));
        UUID mensaje = UUID.fromString(JSON.readTree(eco).get("id").asText());

        assertThat(marcarLeido(global, sesionDe(luis))).isEqualTo(200);

        TimeUnit.SECONDS.sleep(2);
        assertThat(deAna.cuerpos()).noneMatch(cuerpo -> cuerpo.contains("\"event\":\"READ\""));
        assertThat(statusDe(listar(global, sesionDeAna), mensaje)).contains("SENT");
    }

    @Test
    @DisplayName("autorización negativa: quien no participa y la cuenta suspendida reciben 403 y no se suscriben")
    void nadieDeAfueraVeNiMarcaLaLectura() throws Exception {
        String sesionDeMarta = sesionDe(marta);
        String sesionDeRita = sesionDe(rita);

        assertThat(pedir("GET", "/api/v1/chat/conversations/" + directa + "/messages", sesionDeMarta, null).statusCode())
                .as("no participa: no ve la marca").isEqualTo(403);
        assertThat(marcarLeido(directa, sesionDeMarta)).as("no participa: no marca").isEqualTo(403);
        assertThat(pedir("GET", "/api/v1/chat/conversations/" + soporte + "/messages", sesionDeRita, null).statusCode())
                .as("suspendida: no ve la marca").isEqualTo(403);
        assertThat(marcarLeido(soporte, sesionDeRita)).as("suspendida: no marca").isEqualTo(403);

        Cliente marta = conectar(this.marta);
        enviarTrama(marta.socket, "SUBSCRIBE\nid:sub-0\ndestination:/topic/conversaciones/" + directa + "\n\n");
        assertThat(marta.esperarTrama("ERROR", 10)).as("no se suscribe al canal donde viaja el READ").isNotBlank();
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private JsonNode enviar(UUID conversacion, String sesion, String texto) throws Exception {
        HttpResponse<String> respuesta = pedir("POST", "/api/v1/chat/conversations/" + conversacion + "/messages", sesion,
                "{\"type\":\"TEXT\",\"text\":\"" + texto + "\"}");
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(201);
        return JSON.readTree(respuesta.body());
    }

    private int marcarLeido(UUID conversacion, String sesion) throws Exception {
        return pedir("POST", "/api/v1/chat/conversations/" + conversacion + "/read", sesion, null).statusCode();
    }

    private JsonNode listar(UUID conversacion, String sesion) throws Exception {
        HttpResponse<String> respuesta = pedir("GET", "/api/v1/chat/conversations/" + conversacion + "/messages", sesion, null);
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        return JSON.readTree(respuesta.body());
    }

    private static JsonNode mensajeDe(JsonNode pagina, UUID id) {
        for (JsonNode mensaje : pagina.get("messages")) {
            if (mensaje.get("id").asText().equals(id.toString())) {
                return mensaje;
            }
        }
        throw new AssertionError("El listado no trae el mensaje " + id + ": " + pagina);
    }

    /** {@code status} del mensaje, vacío si viaja {@code null}. Falla si el campo no existe (código viejo). */
    private static Optional<String> statusDe(JsonNode pagina, UUID id) {
        JsonNode mensaje = mensajeDe(pagina, id);
        assertThat(mensaje.has("status")).as("el listado trae el campo status").isTrue();
        return mensaje.get("status").isNull() ? Optional.empty() : Optional.of(mensaje.get("status").asText());
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String sesion, String cuerpo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Auth-Token", sesion)
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json");
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ── STOMP ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Accion {
        void ejecutar() throws Exception;
    }

    /**
     * Repite {@code accion} hasta que al cliente le llegue un cuerpo que cumpla {@code condicion}. El
     * broker simple no acusa recibo de un SUBSCRIBE (solo de un DISCONNECT), así que la primera vez la
     * suscripción puede no estar registrada todavía. Repetir es inocuo: la marca de lectura solo
     * avanza.
     */
    private static String hastaQueLlegue(Cliente cliente, Predicate<String> condicion, Accion accion) throws Exception {
        for (int intento = 0; intento < 20; intento++) {
            accion.ejecutar();
            Optional<String> cuerpo = cliente.buscarCuerpo(condicion, 500);
            if (cuerpo.isPresent()) {
                return cuerpo.get();
            }
        }
        throw new AssertionError("No llegó lo esperado tras 20 intentos; llegó: " + cliente.cuerpos());
    }

    /** Conecta y se suscribe; el eco del propio mensaje prueba después que la suscripción ya corre. */
    private Cliente suscribir(UUID quien, UUID conversacion) throws Exception {
        Cliente cliente = conectar(quien);
        enviarTrama(cliente.socket, "SUBSCRIBE\nid:sub-0\ndestination:/topic/conversaciones/" + conversacion + "\n\n");
        return cliente;
    }

    /** Sin latidos ({@code 0,0}): esta prueba no es de latidos y no debe cortarse a los 30 s. */
    private Cliente conectar(UUID quien) throws Exception {
        Cliente cliente = new Cliente();
        cliente.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("X-Auth-Token", sesionDe(quien))
                .buildAsync(URI.create("ws://localhost:" + puerto + "/ws"), cliente)
                .get(10, TimeUnit.SECONDS);
        abiertos.add(cliente.socket);
        enviarTrama(cliente.socket, "CONNECT\naccept-version:1.2\nhost:renaser\nheart-beat:0,0\n\n");
        cliente.esperarTrama("CONNECTED", 10);
        return cliente;
    }

    /** En BINARIO, como la app (E-331). */
    private static void enviarTrama(WebSocket socket, String trama) {
        socket.sendBinary(ByteBuffer.wrap((trama + FIN_DE_TRAMA).getBytes(StandardCharsets.UTF_8)), true).join();
    }

    private static final class Cliente implements WebSocket.Listener {
        final BlockingQueue<String> tramas = new LinkedBlockingQueue<>();
        final List<String> vistas = new ArrayList<>();
        private final StringBuilder parcial = new StringBuilder();
        WebSocket socket;

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

        String esperarTrama(String comando, int segundos) throws InterruptedException {
            return esperar(trama -> trama.startsWith(comando + "\n"), segundos, comando);
        }

        /** El cuerpo de la primera trama MESSAGE que cumpla {@code condicion}, si llega en ese plazo. */
        synchronized Optional<String> buscarCuerpo(Predicate<String> condicion, long milisegundos)
                throws InterruptedException {
            Predicate<String> buscada = trama -> trama.startsWith("MESSAGE\n") && condicion.test(cuerpoDe(trama));
            Optional<String> yaVista = vistas.stream().filter(buscada).findFirst();
            if (yaVista.isPresent()) {
                return yaVista.map(Cliente::cuerpoDe);
            }
            long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milisegundos);
            while (System.nanoTime() < limite) {
                String trama = tramas.poll(50, TimeUnit.MILLISECONDS);
                if (trama != null) {
                    vistas.add(trama);
                    if (buscada.test(trama)) {
                        return Optional.of(cuerpoDe(trama));
                    }
                }
            }
            return Optional.empty();
        }

        synchronized List<String> cuerpos() {
            tramas.drainTo(vistas);
            return vistas.stream().filter(trama -> trama.startsWith("MESSAGE\n")).map(Cliente::cuerpoDe).toList();
        }

        private synchronized String esperar(Predicate<String> condicion, int segundos, String que)
                throws InterruptedException {
            for (String trama : vistas) {
                if (condicion.test(trama)) {
                    return trama;
                }
            }
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(segundos);
            while (System.nanoTime() < limite) {
                String trama = tramas.poll(200, TimeUnit.MILLISECONDS);
                if (trama != null) {
                    vistas.add(trama);
                    if (condicion.test(trama)) {
                        return trama;
                    }
                }
            }
            throw new AssertionError("No llegó " + que + " en " + segundos + " s; llegó: " + vistas);
        }

        private static String cuerpoDe(String trama) {
            String cuerpo = trama.substring(trama.indexOf("\n\n") + 2);
            return cuerpo.endsWith(FIN_DE_TRAMA) ? cuerpo.substring(0, cuerpo.length() - 1) : cuerpo;
        }
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

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

    private UUID usuario(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture', CAST(? AS renaser.rol_usuario), CAST(? AS renaser.estado_usuario))
                """, id, id + "@renaser.test", rol, estado);
        usuarios.add(id);
        return id;
    }

    private UUID conversacion(String tipo, String clave, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.conversaciones (id, tipo, clave_directa, nombre)
                VALUES (?, CAST(? AS renaser.tipo_conversacion), ?, ?)
                """, id, tipo, clave, nombre);
        conversaciones.add(id);
        return id;
    }

    /** Sin marca de lectura, como la deja una fila recién creada por SQL: todavía no leyó nada. */
    private void participa(UUID conversacion, UUID usuario) {
        jdbc.update("INSERT INTO renaser.participantes_conversacion (conversacion_id, usuario_id) VALUES (?, ?)",
                conversacion, usuario);
    }

    /** La comunidad es única (índice parcial): se usa la que haya, o se crea una y se borra al final. */
    private UUID globalConParticipantes(UUID... personas) {
        UUID global = jdbc.query("SELECT id FROM renaser.conversaciones WHERE tipo = 'GLOBAL'",
                        (fila, n) -> fila.getObject(1, UUID.class)).stream().findFirst()
                .orElseGet(() -> conversacion("GLOBAL", null, "Global"));
        for (UUID persona : personas) {
            participa(global, persona);
        }
        return global;
    }
}

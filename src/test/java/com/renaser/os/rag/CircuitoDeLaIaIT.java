package com.renaser.os.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.rag.application.ports.out.ia.ChatIAPort;
import com.renaser.os.rag.application.ports.out.ia.ConversacionEnVivoPort;
import com.renaser.os.rag.application.ports.out.ia.SintetizarVozPort;
import com.renaser.os.rag.application.services.circuito.CopiaDelCircuito;
import com.renaser.os.rag.application.services.circuito.EstadoAutomaticoDeLaIa;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El circuito de SER de punta a punta (spec 002, US2, FR-015 a FR-017; contrato §9 «IA»), sobre el Tomcat real, Redis
 * y Postgres: con un proveedor de prueba que falla, a la quinta falla {@code /funcionalidades/mias} lista la parte
 * apagada con el aviso y cambia la versión y el ETag; el chat responde 503 antes del stream, la voz 204 y la voz en
 * vivo cierra con 1013; vencida la espera, un pedido bueno lo cierra. Cada apertura y cierre deja su fila AUTOMATICO y
 * su push a Soporte TI (como máximo 4 por hora), y un apagado a mano no lo prende el circuito.
 *
 * <p>Lo único doblado es lo que sale a la IA (chat, voz y voz en vivo) y el reloj, que la prueba mueve. Contra el
 * código anterior al circuito, la sexta llamada llegaba al proveedor y la voz apagada daba 503.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, CircuitoDeLaIaIT.ProveedoresDePrueba.class})
class CircuitoDeLaIaIT {

    private static final String AVISO = EstadoAutomaticoDeLaIa.AVISO;
    private static final RelojMovible RELOJ = new RelojMovible();
    private static final Proveedor CHAT = new Proveedor();
    private static final Proveedor VOZ = new Proveedor();
    private static final Proveedor EN_VIVO = new Proveedor();
    private static final AtomicInteger PRUEBAS = new AtomicInteger();

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private CopiaDelCircuito copiaDelCircuito;
    @Autowired
    private CopiaDeEstados copiaDeEstados;

    private PedidosDePrueba pedidos;
    private UUID soporteId;
    private String soporte;
    private Instant base;
    private final Instant inicioReal = Instant.now();

    @BeforeEach
    void armar() {
        // Cada prueba en su propia hora del reloj: las ventanas (fallas, tope de avisos) no se mezclan.
        base = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1L + PRUEBAS.incrementAndGet()));
        RELOJ.fijar(base);
        CHAT.reiniciar();
        VOZ.reiniciar();
        EN_VIVO.reiniciar();
        limpiarRedis();
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporteId = pedidos.usuario("SOPORTE_TI");
        soporte = pedidos.sesionDe(soporteId);
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicioReal);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado");
        copiaDeEstados.recargar();
        limpiarRedis();
        pedidos.borrarCuentas();
    }

    private void limpiarRedis() {
        for (String parte : List.of("ia.chat", "ia.voz")) {
            redis.delete(List.of("renaser:ia:circuito:" + parte, "renaser:ia:circuito:" + parte + ":fallas",
                    "renaser:notificaciones:limite:circuito-ia:" + parte));
        }
        copiaDelCircuito.recargar();
    }

    @Test
    @DisplayName("chat: a la quinta falla se apaga ia.chat con el aviso (versión y ETag nuevos), el chat da 503 antes "
            + "del stream sin llamar al proveedor; vencidos 2 min, un pedido bueno lo cierra; fila y push en cada cambio")
    void chatSeApagaYVuelve() throws Exception {
        String aprendiz = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));
        HttpResponse<String> antes = pedidos.enviar("GET", "/api/v1/funcionalidades/mias", aprendiz, null);
        String etagAntes = antes.headers().firstValue("ETag").orElseThrow();
        long versionAntes = pedidos.json(antes).get("version").asLong();

        CHAT.falla = true;
        for (int i = 1; i <= 4; i++) {
            assertThat(chat(aprendiz).body()).contains("\"tipo\":\"error\"");
        }
        assertThat(apagadas(aprendiz)).as("la cuarta no abre").doesNotContainKey("ia.chat");
        assertThat(chat(aprendiz).statusCode()).isEqualTo(200);

        HttpResponse<String> despues = pedidos.enviar("GET", "/api/v1/funcionalidades/mias", aprendiz, null,
                Map.of("If-None-Match", etagAntes));
        assertThat(despues.statusCode()).as("cambió: no es 304").isEqualTo(200);
        assertThat(despues.headers().firstValue("ETag").orElseThrow()).isNotEqualTo(etagAntes);
        assertThat(pedidos.json(despues).get("version").asLong()).isGreaterThan(versionAntes);
        assertThat(apagadas(aprendiz)).containsEntry("ia.chat", AVISO).doesNotContainKey("ia.voz");

        HttpResponse<String> cortado = chat(aprendiz);
        assertThat(cortado.statusCode()).isEqualTo(503);
        assertThat(cortado.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        JsonNode error = pedidos.json(cortado);
        assertThat(error.get("codigo").asText()).isEqualTo("FUNCIONALIDAD_APAGADA");
        assertThat(error.get("funcionalidad").asText()).isEqualTo("ia.chat");
        assertThat(error.get("message").asText()).isEqualTo(AVISO);
        assertThat(CHAT.llamadas.get()).as("al proveedor no se le pregunta").isEqualTo(5);
        JsonNode circuito = circuitoEnLaConsola("ia.chat");
        assertThat(circuito.get("estado").asText()).isEqualTo("ABIERTO");
        assertThat(circuito.get("fallasEnVentana").asInt()).isEqualTo(5);
        assertThat(Instant.parse(circuito.get("proximaPrueba").asText())).isEqualTo(base.plus(Duration.ofMinutes(2)));

        RELOJ.avanzar(Duration.ofMinutes(2));
        assertThat(apagadas(aprendiz)).as("vencida la espera, la app vuelve a ver SER").doesNotContainKey("ia.chat");
        CHAT.falla = false;
        HttpResponse<String> prueba = chat(aprendiz);

        assertThat(prueba.statusCode()).isEqualTo(200);
        assertThat(prueba.body()).contains("hola").doesNotContain("\"tipo\":\"error\"");
        assertThat(circuitoEnLaConsola("ia.chat").get("estado").asText()).isEqualTo("CERRADO");
        assertThat(chat(aprendiz).statusCode()).isEqualTo(200);
        assertThat(filasAutomaticas("ia.chat")).containsExactly("ABIERTO", "CERRADO");
        pedidos.esperarElOutbox(inicioReal);
        assertThat(pushes()).containsExactly("SER se pausó sola", "SER volvió");
    }

    @Test
    @DisplayName("voz: a la quinta falla del texto a voz, la voz del orbe da 204 y la voz en vivo cierra con 1013 sin "
            + "abrir el proveedor; vencida la espera, un audio bueno cierra el circuito")
    void vozSeApagaYVuelve() throws Exception {
        UUID aprendizId = pedidos.usuario("APRENDIZ");
        String aprendiz = pedidos.sesionDe(aprendizId);
        VOZ.falla = true;
        for (int i = 1; i <= 5; i++) {
            assertThat(voz(aprendiz).statusCode()).as("el audio se genera aparte: 201").isEqualTo(201);
        }
        assertThat(esperarHasta(() -> apagadasSinFallar(aprendiz).containsKey("ia.voz"))).isTrue();
        assertThat(apagadas(aprendiz)).containsEntry("ia.voz", AVISO).doesNotContainKey("ia.chat");

        assertThat(voz(aprendiz).statusCode()).as("la app usa la voz del teléfono").isEqualTo(204);
        assertThat(VOZ.llamadas.get()).isEqualTo(5);
        assertThat(vozEnVivo(aprendiz)).isEqualTo("cierre 1013 no-disponible");
        assertThat(EN_VIVO.llamadas.get()).as("Gemini Live ni se intenta").isZero();
        assertThat(chat(aprendiz).statusCode()).as("el chat sigue: otro circuito").isEqualTo(200);

        RELOJ.avanzar(Duration.ofMinutes(2));
        VOZ.falla = false;
        assertThat(voz(aprendiz).statusCode()).isEqualTo(201);
        assertThat(esperarHasta(() -> !apagadasSinFallar(aprendiz).containsKey("ia.voz"))).isTrue();
        assertThat(circuitoEnLaConsola("ia.voz").get("estado").asText()).isEqualTo("CERRADO");
        assertThat(filasAutomaticas("ia.voz")).containsExactly("ABIERTO", "CERRADO");
    }

    @Test
    @DisplayName("lo manual manda: con ia.chat apagado a mano para Aprendiz, el circuito que cierra (con la prueba de un "
            + "mentor) no se lo prende; ia.voz apagada a mano da 204 y 1013; ia.propuestas apagada da 503")
    void loManualManda() throws Exception {
        String aprendiz = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));
        String mentor = pedidos.sesionDe(pedidos.usuario("MENTOR"));
        cambiar("ia.chat", Map.of("TRAINEE", false), "SER vuelve mañana.");

        CHAT.falla = true;
        for (int i = 0; i < 5; i++) {
            chat(mentor);
        }
        assertThat(apagadas(mentor)).containsEntry("ia.chat", AVISO);
        assertThat(apagadas(aprendiz)).as("con lo manual apagado, su aviso").containsEntry("ia.chat",
                "SER vuelve mañana.");

        RELOJ.avanzar(Duration.ofMinutes(2));
        CHAT.falla = false;
        assertThat(chat(mentor).statusCode()).as("la prueba del mentor").isEqualTo(200);
        assertThat(circuitoEnLaConsola("ia.chat").get("estado").asText()).isEqualTo("CERRADO");
        HttpResponse<String> delAprendiz = chat(aprendiz);
        assertThat(delAprendiz.statusCode()).isEqualTo(503);
        assertThat(pedidos.json(delAprendiz).get("message").asText()).isEqualTo("SER vuelve mañana.");
        assertThat(chat(mentor).statusCode()).isEqualTo(200);

        cambiar("ia.voz", Map.of("TRAINEE", false), "La voz vuelve pronto.");
        assertThat(voz(aprendiz).statusCode()).as("204, no el 503 del interceptor").isEqualTo(204);
        assertThat(vozEnVivo(aprendiz)).isEqualTo("cierre 1013 no-disponible");
        assertThat(EN_VIVO.llamadas.get()).isZero();
        assertThat(voz(mentor).statusCode()).isEqualTo(201);

        cambiar("ia.propuestas", Map.of("TRAINEE", false), null);
        assertThat(pedidos.enviar("POST", "/api/v1/renasia/propuestas/" + UUID.randomUUID() + "/confirmar", aprendiz,
                null).statusCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("un circuito que oscila: seis cambios en 25 min dejan seis filas AUTOMATICO pero solo 4 push")
    void pushComoMaximoCuatroPorHora() throws Exception {
        String mentor = pedidos.sesionDe(pedidos.usuario("MENTOR"));
        CHAT.falla = true;
        for (int i = 0; i < 5; i++) {
            chat(mentor);
        }
        for (Duration espera : List.of(Duration.ofMinutes(2), Duration.ofMinutes(5))) {
            RELOJ.avanzar(espera);
            chat(mentor);
        }
        RELOJ.avanzar(Duration.ofMinutes(15));
        CHAT.falla = false;
        chat(mentor);
        RELOJ.avanzar(Duration.ofMinutes(1));
        CHAT.falla = true;
        for (int i = 0; i < 5; i++) {
            chat(mentor);
        }
        RELOJ.avanzar(Duration.ofMinutes(2));
        CHAT.falla = false;
        chat(mentor);

        assertThat(filasAutomaticas("ia.chat"))
                .containsExactly("ABIERTO", "ABIERTO", "ABIERTO", "CERRADO", "ABIERTO", "CERRADO");
        pedidos.esperarElOutbox(inicioReal);
        assertThat(pushes()).hasSize(4);
    }

    // ---- pedidos -----------------------------------------------------------------------------------------------

    private HttpResponse<String> chat(String sesion) throws Exception {
        return pedidos.enviar("POST", "/api/v1/renasia/mensajes", sesion,
                Map.of("question", "hola", "agent", "COMPANION"), Map.of("Accept", "text/event-stream"));
    }

    private HttpResponse<String> voz(String sesion) throws Exception {
        return pedidos.enviar("POST", "/api/v1/renasia/voz", sesion, Map.of("texto", "Hola"));
    }

    private Map<String, String> apagadas(String sesion) throws Exception {
        Map<String, String> apagadas = new LinkedHashMap<>();
        pedidos.json(pedidos.enviar("GET", "/api/v1/funcionalidades/mias", sesion, null)).get("apagadas")
                .forEach(apagada -> apagadas.put(apagada.get("clave").asText(), apagada.get("aviso").asText(null)));
        return apagadas;
    }

    private Map<String, String> apagadasSinFallar(String sesion) {
        try {
            return apagadas(sesion);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode circuitoEnLaConsola(String parte) throws Exception {
        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/soporte-ti/ia/circuito", soporte, null,
                Map.of("X-Consola-Token", pedidos.desbloquear(soporte)));
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
        for (JsonNode circuito : pedidos.json(respuesta)) {
            if (circuito.get("parte").asText().equals(parte)) {
                return circuito;
            }
        }
        throw new AssertionError("la consola no lista " + parte + ": " + respuesta.body());
    }

    private void cambiar(String clave, Map<String, Boolean> porRol, String aviso) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("prendidaGlobal", true);
        cuerpo.put("porRol", porRol);
        cuerpo.put("aviso", aviso);
        cuerpo.put("motivo", "Prueba del circuito");
        cuerpo.put("version", 0);
        HttpResponse<String> respuesta = pedidos.enviar("PUT", "/api/v1/soporte-ti/funcionalidades/" + clave, soporte,
                cuerpo, Map.of("X-Consola-Token", pedidos.desbloquear(soporte)));
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(200);
    }

    /** Abre la voz en vivo y devuelve cómo se cerró (el aviso de error llega antes y se descarta). */
    private String vozEnVivo(String sesion) throws Exception {
        Cliente cliente = new Cliente();
        HttpClient.newHttpClient().newWebSocketBuilder().header("X-Auth-Token", sesion)
                .buildAsync(URI.create("ws://localhost:" + puerto + "/api/v1/renasia/voz/en-vivo"), cliente)
                .get(10, TimeUnit.SECONDS);
        for (int i = 0; i < 5; i++) {
            String recibido = cliente.recibido.poll(10, TimeUnit.SECONDS);
            if (recibido == null || recibido.startsWith("cierre")) {
                return recibido;
            }
        }
        return null;
    }

    private List<String> filasAutomaticas(String clave) {
        return jdbc.queryForList("""
                SELECT despues->>'estado' FROM renaser.cambios_auditados
                WHERE tipo = 'AUTOMATICO' AND clave = ? AND creado_en >= ? ORDER BY creado_en
                """, String.class, clave, java.sql.Timestamp.from(base));
    }

    private List<String> pushes() {
        return jdbc.queryForList("SELECT titulo FROM renaser.notificaciones WHERE usuario_id = ? "
                + "AND tipo = 'OPERACION_PLATAFORMA' ORDER BY id", String.class, soporteId);
    }

    private static boolean esperarHasta(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            if (condicion.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condicion.getAsBoolean();
    }

    // ---- dobles ------------------------------------------------------------------------------------------------

    /** Lo que sale a la IA, doblado: falla con lo que el adaptador real traduce de un 429/503 de Google. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ProveedoresDePrueba {

        @Bean
        @Primary
        Clock relojDelCircuito() {
            return RELOJ;
        }

        @Bean
        @Primary
        ChatIAPort chatDePrueba() {
            return consulta -> Flux.defer(() -> {
                CHAT.llamadas.incrementAndGet();
                return CHAT.falla ? Flux.error(new ProveedorIaNoDisponibleException("saturado", Duration.ofSeconds(10)))
                        : Flux.just(new EventoRenasia.Texto("hola"), new EventoRenasia.Fin());
            });
        }

        @Bean
        @Primary
        SintetizarVozPort vozDePrueba() {
            return new SintetizarVozPort() {
                @Override
                public boolean disponible() {
                    return true;
                }

                @Override
                public boolean sintetizar(String texto, java.util.function.Consumer<byte[]> destino) {
                    VOZ.llamadas.incrementAndGet();
                    if (VOZ.falla) {
                        throw new ProveedorIaNoDisponibleException("cuota", Duration.ofSeconds(60));
                    }
                    destino.accept(new byte[]{'R', 'I', 'F', 'F'});
                    return true;
                }
            };
        }

        @Bean
        @Primary
        ConversacionEnVivoPort vozEnVivoDePrueba() {
            return new ConversacionEnVivoPort() {
                @Override
                public boolean disponible() {
                    return true;
                }

                @Override
                public SesionEnVivo abrir(Apertura apertura, Oyente oyente) {
                    EN_VIVO.llamadas.incrementAndGet();
                    throw new ConversacionEnVivoNoDisponibleException("la prueba no abre sesiones");
                }
            };
        }
    }

    static final class Proveedor {
        volatile boolean falla;
        final AtomicInteger llamadas = new AtomicInteger();

        void reiniciar() {
            falla = false;
            llamadas.set(0);
        }
    }

    static final class RelojMovible implements Clock {
        private volatile Instant ahora = Instant.now();

        void fijar(Instant instante) {
            ahora = instante;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant now() {
            return ahora;
        }

        @Override
        public LocalDate today() {
            return ahora.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    private static final class Cliente implements WebSocket.Listener {
        final BlockingQueue<String> recibido = new LinkedBlockingQueue<>();
        private final StringBuilder texto = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence datos, boolean ultimo) {
            texto.append(datos);
            if (ultimo) {
                recibido.add(texto.toString());
                texto.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer datos, boolean ultimo) {
            recibido.add("binario " + datos.remaining());
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int codigo, String motivo) {
            recibido.add("cierre " + codigo + " " + motivo);
            return null;
        }
    }
}

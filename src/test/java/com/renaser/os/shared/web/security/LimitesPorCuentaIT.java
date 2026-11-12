package com.renaser.os.shared.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import com.renaser.os.shared.domain.GrupoDeLimite;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El límite por cuenta de punta a punta (spec 050, tramo C), contra el Tomcat, el Postgres y el Redis reales y la cadena de filtros de
 * verdad: {@code REGISTRAR} cuenta y anota pero no bloquea, {@code APLICAR} responde 429 con {@code Retry-After} y el cuerpo del
 * contrato, el modo se cambia en Redis sin reiniciar y vuelve, lo exento no se cuenta y «ver como» se rechaza antes de contarse.
 *
 * <p>Las pruebas usan el Muro ({@code POST /wall}: 10 por minuto) y las subidas ({@code POST …/upload-url}: 20 por minuto). Los pedidos
 * llevan un cuerpo vacío a propósito: llegan al controller (o no) y responden 4xx, pero no crean nada. Lo que se mira es cuántos 429 salen
 * y qué queda en Redis. El modo de un grupo se fija en Redis y se devuelve al terminar: el contexto lo comparten otras clases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LimitesPorCuentaIT {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ObjectMapper JSON = new ObjectMapper();

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
    private MeterRegistry metricas;
    @Autowired
    private RelojDePrueba reloj;

    private PedidosDePrueba pedidos;
    private UUID cuenta;
    private String token;
    private final List<String> sesionesExtra = new ArrayList<>();

    @BeforeEach
    void armar() {
        reloj.fijar(Instant.now());
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        cuenta = pedidos.usuario("APRENDIZ");
        token = pedidos.sesionDe(cuenta);
    }

    /** Devuelve los grupos a la configuración, fuerza a releer y borra lo que la prueba dejó. */
    @AfterEach
    void limpiar() throws Exception {
        borrarElModoDeTodosLosGrupos();
        reloj.avanzar(Duration.ofSeconds(31));
        pedidos.enviar("GET", "/api/v1/auth/me", token, null);
        sesionesExtra.forEach(sesiones::deleteById);
        Set<String> sobras = redis.keys("proteccion:habria:*");
        if (sobras != null) {
            sobras.forEach(clave -> redis.opsForHash().delete(clave, cuenta.toString()));
        }
        pedidos.borrarCuentas();
    }

    private void borrarElModoDeTodosLosGrupos() {
        for (GrupoDeLimite grupo : GrupoDeLimite.values()) {
            redis.delete("proteccion:modo:" + grupo.clave());
        }
    }

    /** Fija el modo en Redis y avanza el reloj de la app para que el filtro lo relea (el caché es de 30 s). */
    private void fijarElModo(GrupoDeLimite grupo, String modo) {
        redis.opsForValue().set("proteccion:modo:" + grupo.clave(), modo);
        reloj.avanzar(Duration.ofSeconds(31));
    }

    private void quitarElModo(GrupoDeLimite grupo) {
        redis.delete("proteccion:modo:" + grupo.clave());
        reloj.avanzar(Duration.ofSeconds(31));
    }

    private HttpResponse<String> publicar() throws Exception {
        return pedidos.enviar("POST", "/api/v1/wall", token, Map.of());
    }

    private HttpResponse<String> pedirUnaSubida() throws Exception {
        return pedidos.enviar("POST", "/api/v1/wall/media/upload-url", token, Map.of());
    }

    private Set<Integer> estados(int pedidosAEnviar, java.util.concurrent.Callable<HttpResponse<String>> pedido) throws Exception {
        Set<Integer> estados = new TreeSet<>();
        for (int i = 0; i < pedidosAEnviar; i++) {
            estados.add(pedido.call().statusCode());
        }
        return estados;
    }

    private double contado(String grupo, String resultado) {
        Counter contador = metricas.find("renaser.limite.pedidos").tags("grupo", grupo, "resultado", resultado).counter();
        return contador == null ? 0 : contador.count();
    }

    // spec 050 FR-016
    @Test
    @DisplayName("REGISTRAR (el modo de partida): 14 publicaciones en un minuto, ningún 429; queda anotado quién habría bloqueado y las métricas lo cuentan")
    void registrarNoBloqueaPeroAnota() throws Exception {
        double habriaAntes = contado("muro", "habria_bloqueado");
        double permitidoAntes = contado("muro", "permitido");

        Set<Integer> estados = estados(14, this::publicar);

        assertThat(estados).as("en REGISTRAR nunca sale un 429").doesNotContain(429);
        String hash = "proteccion:habria:muro:" + reloj.now().atZone(LIMA).toLocalDate();
        assertThat(redis.opsForHash().get(hash, cuenta.toString())).as("los pedidos 11 a 14 habrían sido bloqueados").isEqualTo("4");
        assertThat(redis.getExpire(hash, TimeUnit.DAYS)).as("el registro vive 30 días").isBetween(29L, 30L);
        assertThat(contado("muro", "habria_bloqueado") - habriaAntes).isEqualTo(4);
        assertThat(contado("muro", "permitido") - permitidoAntes).isEqualTo(10);
        assertThat(metricas.find("renaser.limite.uso").tags("grupo", "muro", "ventana", "1m").summary()).isNotNull();
    }

    // spec 050 FR-015
    @Test
    @DisplayName("APLICAR: la publicación 11 del minuto recibe 429 con Retry-After y el cuerpo del contrato, con las cabeceras de CORS; lo demás de la cuenta sigue")
    void aplicarResponde429ConElContrato() throws Exception {
        fijarElModo(GrupoDeLimite.MURO, "APLICAR");

        assertThat(estados(10, this::publicar)).as("las diez primeras llegan al controller").doesNotContain(429);
        HttpResponse<String> onceava = pedidos.enviar("POST", "/api/v1/wall", token, Map.of(),
                Map.of("Origin", "http://localhost:8081"));
        HttpResponse<String> doceava = publicar();

        assertThat(onceava.statusCode()).isEqualTo(429);
        assertThat(doceava.statusCode()).isEqualTo(429);
        long segundos = Long.parseLong(onceava.headers().firstValue("Retry-After").orElseThrow());
        assertThat(segundos).isBetween(1L, 60L);
        assertThat(onceava.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(onceava.headers().firstValue("Access-Control-Allow-Origin")).as("el 429 lleva el CORS que puso Spring Security")
                .contains("http://localhost:8081");
        assertThat(onceava.headers().firstValue("Access-Control-Expose-Headers")).as("el build web puede leer Retry-After")
                .hasValueSatisfying(expuestas -> assertThat(expuestas).contains("Retry-After"));
        JsonNode cuerpo = JSON.readTree(onceava.body());
        assertThat(cuerpo.get("message").asText()).isEqualTo("Espera un momento y vuelve a intentarlo.");
        assertThat(cuerpo.get("codigo").asText()).isEqualTo("DEMASIADOS_PEDIDOS");
        assertThat(Instant.parse(cuerpo.get("timestamp").asText())).isNotNull();
        assertThat(onceava.body()).doesNotContain("muro", "MURO");

        assertThat(pedidos.enviar("GET", "/api/v1/auth/me", token, null).statusCode())
                .as("otro grupo (lectura) de la misma cuenta no se ve afectado").isEqualTo(200);
        assertThat(contado("muro", "bloqueado")).isGreaterThanOrEqualTo(2);
    }

    // spec 050 FR-016
    @Test
    @DisplayName("el modo se cambia en Redis SIN reiniciar, en los dos sentidos, y un valor que no es un modo se ignora")
    void elModoSeCambiaSinReiniciar() throws Exception {
        estados(12, this::publicar);
        assertThat(publicar().statusCode()).as("REGISTRAR: pasa aunque ya se pasó").isNotEqualTo(429);

        fijarElModo(GrupoDeLimite.MURO, "APLICAR");
        assertThat(publicar().statusCode()).as("APLICAR: el mismo minuto, la misma cuenta, ahora 429").isEqualTo(429);

        fijarElModo(GrupoDeLimite.MURO, "BLOQUEAR");
        assertThat(publicar().statusCode()).as("un valor que no es un modo se ignora y rige el de la configuración (REGISTRAR)")
                .isNotEqualTo(429);

        fijarElModo(GrupoDeLimite.MURO, "aplicar");
        assertThat(publicar().statusCode()).as("el valor no distingue mayúsculas").isEqualTo(429);

        quitarElModo(GrupoDeLimite.MURO);
        assertThat(publicar().statusCode()).as("sin valor en Redis vuelve al de la configuración").isNotEqualTo(429);

        fijarElModo(GrupoDeLimite.MURO, "DESACTIVADO");
        double antes = contado("muro", "permitido") + contado("muro", "habria_bloqueado");
        publicar();
        assertThat(contado("muro", "permitido") + contado("muro", "habria_bloqueado")).as("DESACTIVADO no cuenta nada").isEqualTo(antes);
    }

    // spec 050 FR-013
    @Test
    @DisplayName("subidas en APLICAR: la URL de subida 21 del minuto recibe 429 (las 20 primeras no)")
    void lasSubidasTienenSuTope() throws Exception {
        fijarElModo(GrupoDeLimite.SUBIDAS, "APLICAR");

        assertThat(estados(20, this::pedirUnaSubida)).doesNotContain(429);

        assertThat(pedirUnaSubida().statusCode()).isEqualTo(429);
        assertThat(publicar().statusCode()).as("el Muro es otro grupo").isNotEqualTo(429);
    }

    // spec 050 FR-021
    @Test
    @DisplayName("una cuenta en «ver como» recibe su 403 de siempre ANTES de contarse, aunque el grupo esté en APLICAR")
    void verComoSeRechazaAntesDeContarse() throws Exception {
        fijarElModo(GrupoDeLimite.MURO, "APLICAR");
        UUID prueba = pedidos.usuario("APRENDIZ");
        String sesionDeSoloLectura = sesionDeSoloLectura(sesiones, prueba);

        HttpResponse<String> respuesta = pedidos.enviar("POST", "/api/v1/wall", sesionDeSoloLectura, Map.of());

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.body()).contains("MODO_SOLO_LECTURA");
        assertThat(redis.keys("prot:lim:muro:*:c:" + prueba)).as("ni siquiera se contó").isEmpty();
        assertThat(pedidos.enviar("GET", "/api/v1/auth/me", sesionDeSoloLectura, null).statusCode()).isEqualTo(200);
        assertThat(redis.keys("prot:lim:lectura:*:c:" + prueba)).as("lo que sí se pudo hacer, la lectura, sí se cuenta").isNotEmpty();
    }

    // spec 050 FR-013
    @Test
    @DisplayName("sin sesión, el webhook de WhatsApp se cuenta por el origen que pone CloudFront; el login y los demás públicos no se cuentan")
    void lasRutasPublicasYElWebhook() throws Exception {
        String ip = "203.0.113." + (1 + ThreadLocalRandom.current().nextInt(250));

        anonimo("POST", "/api/v1/webhooks/whatsapp", ip);
        anonimo("POST", "/api/v1/webhooks/whatsapp", ip);
        anonimo("POST", "/api/v1/auth/login", ip);
        anonimo("POST", "/api/v1/auth/login", ip);

        assertThat(redis.opsForValue().get("prot:lim:webhook-whatsapp:1m:ip:" + ip)).isEqualTo("2");
        assertThat(redis.keys("prot:lim:*:ip:" + ip)).as("el login no tiene grupo: solo existe la clave del webhook")
                .containsExactly("prot:lim:webhook-whatsapp:1m:ip:" + ip);
    }

    private void anonimo(String metodo, String ruta, String ipDeCloudFront) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .header("Content-Type", "application/json").header("X-Forwarded-For", "6.6.6.6, " + ipDeCloudFront).build();
        HttpClient.newHttpClient().send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private <S extends Session> String sesionDeSoloLectura(SessionRepository<S> repositorio, UUID usuario) {
        S sesion = repositorio.createSession();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario.toString(), null, AuthorityUtils.NO_AUTHORITIES));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        sesion.setAttribute(SesionDeSoloLectura.ABIERTA_POR, UUID.randomUUID().toString());
        sesion.setAttribute(SesionDeSoloLectura.VENCE_EN, Instant.now().plus(Duration.ofMinutes(10)).toEpochMilli());
        sesion.setAttribute(SesionDeSoloLectura.ROL, "MENTOR");
        repositorio.save(sesion);
        sesionesExtra.add(sesion.getId());
        return sesion.getId();
    }
}

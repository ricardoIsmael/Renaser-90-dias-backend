package com.renaser.os.shared.web.security;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El tope absoluto de la sesión de punta a punta (spec 050, tramo E; FR-028 a FR-031), contra el Tomcat, el Postgres y el
 * Redis reales: las sesiones son las de Spring Session, y la edad de cada una se fija escribiendo su {@code creationTime}
 * en el hash de Redis (es lo que lee {@code HttpSession.getCreationTime()}), con el reloj de la aplicación entre las
 * 00:00 y las 05:00 UTC (FR-031).
 *
 * <p>Falla sin el cambio: sin {@code FiltroDeTopeDeSesion} la sesión de 91 días responde 200. Corre en su propio contexto
 * ({@code properties = …=90d}): en el resto de la suite el tope va apagado (ver {@code src/test/resources/application.yaml}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "renaser.sesion.tope-absoluto=90d")
@Import(TestcontainersConfiguration.class)
class TopeAbsolutoDeSesionIT {

    /** 02:30 UTC: en Lima todavía es el día anterior; ahí se escondía el bug del reloj (E-91). */
    private static final Instant AHORA = Instant.parse("2026-11-20T02:30:00Z");
    private static final String ME = "/api/v1/auth/me";
    private static final String CREACION = "creationTime";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private RedisConnectionFactory conexiones;
    @Autowired
    @Qualifier("springSessionDefaultRedisSerializer")
    private RedisSerializer<Object> serializador;
    @Value("${spring.session.data.redis.namespace}")
    private String espacio;

    private PedidosDePrueba pedidos;
    private UUID cuenta;

    @BeforeEach
    void armar() {
        reloj.fijar(AHORA);
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        cuenta = pedidos.usuario("ADMIN");
    }

    @AfterEach
    void limpiar() {
        pedidos.borrarCuentas();
    }

    /** Una sesión real de la cuenta, con la edad que se pide: la «creó» hace tantos días, pero se usó hace un momento. */
    private String sesionDeHaceDias(long dias) {
        String token = pedidos.sesionDe(cuenta);
        fijarLaCreacion(token, AHORA.minus(Duration.ofDays(dias)));
        return token;
    }

    private void fijarLaCreacion(String token, Instant creada) {
        byte[] clave = (espacio + ":sessions:" + token).getBytes(StandardCharsets.UTF_8);
        byte[] campo = CREACION.getBytes(StandardCharsets.UTF_8);
        try (RedisConnection redis = conexiones.getConnection()) {
            byte[] antes = redis.hashCommands().hGet(clave, campo);
            assertThat(serializador.deserialize(antes)).as("el creationTime que escribe Spring Session es un Long")
                    .isInstanceOf(Long.class);
            redis.hashCommands().hSet(clave, campo, serializador.serialize(creada.toEpochMilli()));
        }
    }

    private HttpResponse<String> pedirMiPerfil(String token) throws Exception {
        return pedidos.enviar("GET", ME, token, null);
    }

    // spec 050 FR-028, FR-031, SC-007
    @Test
    @DisplayName("una sesión iniciada hace 91 días y usada hace un momento es 401 SESION_VENCIDA, y queda invalidada")
    void laDe91DiasEs401YSeInvalida() throws Exception {
        String token = sesionDeHaceDias(91);

        HttpResponse<String> respuesta = pedirMiPerfil(token);

        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(401);
        assertThat(respuesta.body()).contains("\"codigo\":\"SESION_VENCIDA\"")
                .contains("Tu sesión venció. Vuelve a iniciar sesión.");
        assertThat(sesiones.findById(token)).as("la sesión se borró de Redis").isNull();
        HttpResponse<String> otraVez = pedirMiPerfil(token);
        assertThat(otraVez.statusCode()).as("el mismo token ya no es de nadie").isNotEqualTo(200);
        assertThat(otraVez.body()).doesNotContain("SESION_VENCIDA");
    }

    // spec 050 FR-028, FR-031
    @Test
    @DisplayName("la de 89 días y la de 20 días siguen vigentes: 200 con su perfil")
    void lasQueNoLlegaronSiguen() throws Exception {
        for (long dias : new long[] {20, 89}) {
            String token = sesionDeHaceDias(dias);

            HttpResponse<String> respuesta = pedirMiPerfil(token);

            assertThat(respuesta.statusCode()).as(dias + " días: " + respuesta.body()).isEqualTo(200);
            assertThat(respuesta.body()).contains(cuenta.toString());
            assertThat(sesiones.findById(token)).as(dias + " días: sigue en Redis").isNotNull();
        }
    }

    // spec 050 FR-028
    @Test
    @DisplayName("pasa a vencer cuando el reloj cruza los 90 días, no antes: la misma sesión vale hoy y no vale mañana")
    void vencePorElPasoDelReloj() throws Exception {
        String token = sesionDeHaceDias(90);
        reloj.avanzar(Duration.ofHours(-1));
        assertThat(pedirMiPerfil(token).statusCode()).as("una hora antes de cumplir los 90 días").isEqualTo(200);

        reloj.avanzar(Duration.ofHours(2));
        HttpResponse<String> despues = pedirMiPerfil(token);

        assertThat(despues.statusCode()).as("una hora después").isEqualTo(401);
        assertThat(despues.body()).contains("SESION_VENCIDA");
    }

    // spec 050 FR-029
    @Test
    @DisplayName("los 30 días de inactividad siguen igual: una sesión nueva nace con 30 días de plazo sin uso")
    void laInactividadSigueEnTreintaDias() {
        Session nueva = sesiones.createSession();

        assertThat(nueva.getMaxInactiveInterval()).isEqualTo(Duration.ofDays(30));
    }

    // spec 050 FR-028
    @Test
    @DisplayName("quien trae un token de 91 días puede entrar de nuevo: el login pasa y entrega una sesión nueva")
    void elLoginPasaConUnTokenVencido() throws Exception {
        String vencida = sesionDeHaceDias(91);

        HttpResponse<String> login = pedidos.enviar("POST", "/api/v1/auth/login", vencida,
                Map.of("email", cuenta + "@renaser.test", "contrasena", PedidosDePrueba.CONTRASENA));

        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        String nueva = login.headers().firstValue("X-Auth-Token").orElseThrow();
        assertThat(nueva).as("sesión nueva").isNotEqualTo(vencida);
        assertThat(sesiones.findById(vencida)).as("la vieja se invalidó").isNull();
        HttpResponse<String> conLaNueva = pedirMiPerfil(nueva);
        assertThat(conLaNueva.statusCode()).as(conLaNueva.body()).isEqualTo(200);
    }
}

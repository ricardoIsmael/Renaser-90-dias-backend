package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-678, por HTTP de verdad (servidor Tomcat y Redis reales, con la configuracion de produccion): el limite de intentos
 * de login por (origen, correo) cuenta contra la IP que agrega CloudFront, el valor de mas a la derecha de
 * {@code X-Forwarded-For}, y no contra lo que escriba el cliente a su izquierda ni en {@code Forwarded}.
 *
 * <p>Los tres casos fallan contra el codigo anterior: con el filtro de cabeceras reenviadas de Spring la IP era el primer
 * valor, y cada intento con otro valor a la izquierda estrenaba cupo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class IpDelClienteIT {

    /** {@code AutenticacionService.LIMITE_POR_EMAIL_Y_ORIGEN}: el intento once desde el mismo origen rebota. */
    private static final int INTENTOS_CON_CUPO = 10;
    private static final String CLIENTE_REAL = "203.0.113.50";
    private static final String OTRO_CLIENTE_REAL = "203.0.113.51";

    @LocalServerPort
    private int puerto;
    @Autowired
    private StringRedisTemplate redis;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void vaciarContadores() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @AfterEach
    void dejarLosContadoresVacios() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private HttpResponse<String> intentarEntrar(String correo, Map<String, String> cabeceras) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + correo + "\",\"contrasena\":\"no-es-la-contrasena\"}", StandardCharsets.UTF_8));
        cabeceras.forEach(pedido::header);
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String correoNuevo() {
        return "ip-" + UUID.randomUUID() + "@renaser.test";
    }

    private void gastarElCupoDe(String correo, String clienteReal) throws Exception {
        for (int i = 1; i <= INTENTOS_CON_CUPO; i++) {
            HttpResponse<String> r = intentarEntrar(correo, Map.of("X-Forwarded-For", "198.51.100." + i + ", " + clienteReal));
            assertThat(r.statusCode()).as("intento %d con cupo", i).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("el mismo cliente real con un valor distinto a la izquierda en cada intento gasta el mismo cupo: el once rebota con 429")
    void elValorEscritoPorElClienteNoEstrenaCupo() throws Exception {
        String correo = correoNuevo();

        gastarElCupoDe(correo, CLIENTE_REAL);

        HttpResponse<String> intentoOnce = intentarEntrar(correo, Map.of("X-Forwarded-For", "198.51.100.99, " + CLIENTE_REAL));
        assertThat(intentoOnce.statusCode()).isEqualTo(429);
    }

    @Test
    @DisplayName("otro cliente real, con el mismo correo, tiene su propio cupo: el limite no es un contador de toda la gente")
    void otroClienteRealNoQuedaBloqueado() throws Exception {
        String correo = correoNuevo();
        gastarElCupoDe(correo, CLIENTE_REAL);

        HttpResponse<String> otro = intentarEntrar(correo, Map.of("X-Forwarded-For", "198.51.100.1, " + OTRO_CLIENTE_REAL));

        assertThat(otro.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("la cabecera Forwarded no cambia de quien es la peticion")
    void laCabeceraForwardedNoCuenta() throws Exception {
        String correo = correoNuevo();
        for (int i = 1; i <= INTENTOS_CON_CUPO; i++) {
            HttpResponse<String> r = intentarEntrar(correo,
                    Map.of("X-Forwarded-For", "198.51.100.7, " + CLIENTE_REAL, "Forwarded", "for=198.51.100." + i));
            assertThat(r.statusCode()).as("intento %d con cupo", i).isEqualTo(401);
        }

        HttpResponse<String> intentoOnce = intentarEntrar(correo,
                Map.of("X-Forwarded-For", "198.51.100.7, " + CLIENTE_REAL, "Forwarded", "for=198.51.100.99"));

        assertThat(intentoOnce.statusCode()).isEqualTo(429);
    }
}

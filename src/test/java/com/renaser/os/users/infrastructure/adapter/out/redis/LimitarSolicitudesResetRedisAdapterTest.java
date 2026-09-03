package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LimitarSolicitudesResetRedisAdapterTest {

    /**
     * Identico al prefijo privado {@code LimitarSolicitudesResetRedisAdapter.CLAVE_PREFIJO}
     * (mismo archivo, mismo paquete) — se duplica aca porque el campo es {@code private}, no
     * package-private. Si ese literal cambia, este test hay que actualizarlo a mano (mismo
     * criterio que usa {@code AccountRequestRateLimitConcurrenciaTest} para su limite espejado).
     */
    private static final String CLAVE_PREFIJO = "reset-password:rl:";

    @Autowired
    private LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void permiteHastaElMaximoYDespuesRechaza() {
        String clave = "email:limite-" + UUID.randomUUID();

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMinutes(1), 3)).isFalse();
    }

    @Test
    void clavesDistintasTienenContadoresIndependientes() {
        String claveEmail = "email:independiente-" + UUID.randomUUID();
        String claveIp = "ip:independiente-" + UUID.randomUUID();

        assertThat(limitarSolicitudesResetPort.registrarIntento(claveEmail, Duration.ofMinutes(1), 1)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(claveEmail, Duration.ofMinutes(1), 1)).isFalse();
        // La clave de IP no se vio afectada por haber agotado la de email.
        assertThat(limitarSolicitudesResetPort.registrarIntento(claveIp, Duration.ofMinutes(1), 1)).isTrue();
    }

    @Test
    void laVentanaExpiraYReiniciaElContador() throws InterruptedException {
        String clave = "email:ventana-" + UUID.randomUUID();

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMillis(500), 1)).isTrue();
        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMillis(500), 1)).isFalse();

        Thread.sleep(900);

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofMillis(500), 1)).isTrue();
    }

    @Test
    @DisplayName("C-8: el TTL se fija en la primera llamada y NO se renueva en llamadas "
            + "posteriores dentro de la misma ventana (si se renovara, el limite dejaria de ser "
            + "\"N por ventana\" y pasaria a ser \"N intentos seguidos sin pausa\")")
    void elTtlSeFijaUnaSolaVezYNoSeRenuevaEnLlamadasPosteriores() throws InterruptedException {
        String clave = "email:ttl-" + UUID.randomUUID();
        String claveRedis = CLAVE_PREFIJO + clave;

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofSeconds(10), 5)).isTrue();
        Long ttlTrasPrimeraLlamada = redisTemplate.getExpire(claveRedis, TimeUnit.MILLISECONDS);
        assertThat(ttlTrasPrimeraLlamada).isPositive();

        Thread.sleep(400);

        assertThat(limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofSeconds(10), 5)).isTrue();
        Long ttlTrasSegundaLlamada = redisTemplate.getExpire(claveRedis, TimeUnit.MILLISECONDS);

        // El TTL debe haber seguido corriendo (bajado en los ~400ms dormidos), no haberse
        // reiniciado a los 10000ms completos de la ventana original.
        assertThat(ttlTrasSegundaLlamada).isLessThan(ttlTrasPrimeraLlamada);
    }

    @Test
    @DisplayName("C-8: una clave envenenada (existe, sin TTL — el estado que dejaba el codigo "
            + "viejo si el proceso moria entre INCR y EXPIRE) se autorepara en la siguiente "
            + "llamada, en vez de quedar bloqueada para siempre")
    void unaClaveEnvenenadaSinTtlSeAutoreparaEnLaSiguienteLlamada() {
        String clave = "email:envenenada-" + UUID.randomUUID();
        String claveRedis = CLAVE_PREFIJO + clave;
        // Simula el estado que dejaba el codigo viejo: la clave ya existe (con un valor por
        // encima del maximo, como si llevara bloqueando desde antes) pero SIN TTL.
        redisTemplate.opsForValue().set(claveRedis, "999");
        assertThat(redisTemplate.getExpire(claveRedis)).isEqualTo(-1L);

        boolean permitido = limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofSeconds(10), 1000);

        assertThat(permitido).isTrue();
        assertThat(redisTemplate.getExpire(claveRedis)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("el limite se respeta bajo llamadas concurrentes (INCR + TTL atomicos via Lua)")
    void elLimiteSeRespetaBajoConcurrencia() throws InterruptedException {
        String clave = "email:concurrencia-" + UUID.randomUUID();
        int maximo = 20;
        int intentosConcurrentes = maximo + 15;

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> resultados;
        try {
            List<Callable<Boolean>> tareas = IntStream.range(0, intentosConcurrentes)
                    .<Callable<Boolean>>mapToObj(i -> () ->
                            limitarSolicitudesResetPort.registrarIntento(clave, Duration.ofSeconds(30), maximo))
                    .toList();
            resultados = pool.invokeAll(tareas, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        long exitosos = resultados.stream().filter(LimitarSolicitudesResetRedisAdapterTest::obtenerResultado).count();

        assertThat(exitosos).as("nunca deben aceptarse mas de %s intentos para la misma clave", maximo)
                .isEqualTo(maximo);
    }

    private static boolean obtenerResultado(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado en un intento concurrente", e);
        }
    }
}

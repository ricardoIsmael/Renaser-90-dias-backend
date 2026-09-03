package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Redis real — el punto central es que el limite de intentos sea de verdad (OWASP
 * Multifactor Authentication Cheat Sheet: "apply strict attempt limits"), no solo el TTL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CodigoVerificacionEmailRedisAdapterTest {

    /**
     * Identico al prefijo privado {@code CodigoVerificacionEmailRedisAdapter.PREFIJO_INTENTOS}
     * (mismo archivo, mismo paquete) — se duplica aca porque el campo es {@code private}, no
     * package-private. Si ese literal cambia, este test hay que actualizarlo a mano.
     */
    private static final String PREFIJO_INTENTOS = "email-verification:intentos:";

    @Autowired
    private CodigoVerificacionEmailPort codigoVerificacionEmailPort;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void generarYVerificarConElCodigoCorrectoTieneExito() {
        String email = "codigo1@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5)).isTrue();
    }

    @Test
    void elCodigoEsDe6DigitosNumericos() {
        String codigo = codigoVerificacionEmailPort.generarCodigo("codigo-forma@renaser.dev", Duration.ofMinutes(10));

        assertThat(codigo).hasSize(6).matches("\\d{6}");
    }

    @Test
    @DisplayName("un codigo correcto se consume: verificarlo dos veces la segunda falla")
    void unCodigoCorrectoEsDeUnSoloUso() {
        String email = "codigo2@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        boolean primerIntento = codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5);
        boolean segundoIntento = codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5);

        assertThat(primerIntento).isTrue();
        assertThat(segundoIntento).isFalse();
    }

    @Test
    void unCodigoIncorrectoNoTieneExito() {
        String email = "codigo3@renaser.dev";
        codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, "000000", 5)).isFalse();
    }

    @Test
    @DisplayName("verificar un codigo para un email que nunca pidio uno falla, no explota")
    void verificarSinHaberGeneradoNuncaDevuelveFalse() {
        assertThat(codigoVerificacionEmailPort.verificarCodigo("nunca-pidio@renaser.dev", "123456", 5)).isFalse();
    }

    @Test
    @DisplayName("OWASP MFA Cheat Sheet: al agotar los intentos, el codigo se invalida por "
            + "completo — ni siquiera el codigo CORRECTO sirve despues")
    void agotarLosIntentosInvalidaElCodigoAunqueDespuesSeIntenteElCorrecto() {
        String email = "codigo4@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));
        int maxIntentos = 5;

        for (int i = 0; i < maxIntentos; i++) {
            boolean resultado = codigoVerificacionEmailPort.verificarCodigo(email, "000000", maxIntentos);
            assertThat(resultado).isFalse();
        }

        // El codigo real ya no deberia funcionar: se agotaron los intentos permitidos.
        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, maxIntentos)).isFalse();
    }

    @Test
    @DisplayName("intentos fallidos por debajo del maximo no invalidan el codigo real")
    void intentosFallidosPorDebajoDelMaximoNoInvalidanElCodigo() {
        String email = "codigo5@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));
        int maxIntentos = 5;

        for (int i = 0; i < maxIntentos - 1; i++) {
            codigoVerificacionEmailPort.verificarCodigo(email, "000000", maxIntentos);
        }

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, maxIntentos)).isTrue();
    }

    @Test
    @DisplayName("pedir un codigo nuevo resetea el contador de intentos del email")
    void generarUnCodigoNuevoReseteaLosIntentosFallidosDelAnterior() {
        String email = "codigo6@renaser.dev";
        String codigoViejo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));
        int maxIntentos = 5;
        for (int i = 0; i < maxIntentos; i++) {
            codigoVerificacionEmailPort.verificarCodigo(email, "000000", maxIntentos);
        }
        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigoViejo, maxIntentos))
                .as("el codigo viejo ya deberia estar invalidado por agotar intentos")
                .isFalse();

        String codigoNuevo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigoNuevo, maxIntentos)).isTrue();
    }

    @Test
    void unCodigoVencidoYaNoSePuedeVerificar() throws InterruptedException {
        String email = "codigo7@renaser.dev";
        String codigo = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofMillis(500));

        Thread.sleep(900);

        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigo, 5)).isFalse();
    }

    @Test
    void dosEmailsDistintosTienenCodigosIndependientes() {
        String codigoA = codigoVerificacionEmailPort.generarCodigo("codigo8a@renaser.dev", Duration.ofMinutes(10));
        String codigoB = codigoVerificacionEmailPort.generarCodigo("codigo8b@renaser.dev", Duration.ofMinutes(10));

        assertThat(codigoVerificacionEmailPort.verificarCodigo("codigo8a@renaser.dev", codigoB, 5)).isFalse();
        assertThat(codigoVerificacionEmailPort.verificarCodigo("codigo8a@renaser.dev", codigoA, 5)).isTrue();
    }

    @Test
    @DisplayName("C-8: el TTL de intentos se fija en el primer intento fallido, copiado del "
            + "TTL restante del codigo, y NO se renueva en fallos posteriores")
    void elTtlDeIntentosSeFijaUnaVezDesdeElCodigoYNoSeRenueva() throws InterruptedException {
        String email = "codigo9@renaser.dev";
        codigoVerificacionEmailPort.generarCodigo(email, Duration.ofSeconds(30));

        codigoVerificacionEmailPort.verificarCodigo(email, "000000", 10);
        Long ttlTrasPrimerFallo = redisTemplate.getExpire(claveIntentos(email), TimeUnit.MILLISECONDS);
        assertThat(ttlTrasPrimerFallo).isPositive();

        Thread.sleep(400);

        codigoVerificacionEmailPort.verificarCodigo(email, "000000", 10);
        Long ttlTrasSegundoFallo = redisTemplate.getExpire(claveIntentos(email), TimeUnit.MILLISECONDS);

        // Debe haber seguido corriendo (bajado en los ~400ms dormidos), no haberse
        // reiniciado al TTL completo del codigo.
        assertThat(ttlTrasSegundoFallo).isLessThan(ttlTrasPrimerFallo);
    }

    @Test
    @DisplayName("C-8: una clave de intentos envenenada (existe, sin TTL) se autorepara en el "
            + "siguiente fallo mientras el codigo siga vigente")
    void unaClaveDeIntentosEnvenenadaSinTtlSeAutoreparaEnElSiguienteFallo() {
        String email = "codigo10@renaser.dev";
        codigoVerificacionEmailPort.generarCodigo(email, Duration.ofSeconds(30));
        // Simula el estado que dejaba el codigo viejo: la clave de intentos ya existe
        // (como si llevara varios fallos contados) pero SIN TTL propio.
        redisTemplate.opsForValue().set(claveIntentos(email), "3");
        assertThat(redisTemplate.getExpire(claveIntentos(email))).isEqualTo(-1L);

        codigoVerificacionEmailPort.verificarCodigo(email, "000000", 10);

        assertThat(redisTemplate.getExpire(claveIntentos(email))).isGreaterThan(0L);
    }

    @Test
    @DisplayName("el limite de intentos se respeta bajo fallos concurrentes (INCR + TTL "
            + "atomicos via Lua)")
    void elLimiteDeIntentosSeRespetaBajoConcurrencia() throws InterruptedException {
        String email = "codigo11@renaser.dev";
        String codigoReal = codigoVerificacionEmailPort.generarCodigo(email, Duration.ofSeconds(30));
        int maxIntentos = 20;
        int fallosConcurrentes = maxIntentos + 15;

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> resultados;
        try {
            List<Callable<Boolean>> tareas = IntStream.range(0, fallosConcurrentes)
                    .<Callable<Boolean>>mapToObj(i -> () ->
                            codigoVerificacionEmailPort.verificarCodigo(email, "000000", maxIntentos))
                    .toList();
            resultados = pool.invokeAll(tareas, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        // Todos los intentos fueron con un codigo incorrecto, asi que ninguno tiene exito;
        // lo que se demuestra es que el codigo real deja de servir apenas se llega al maximo
        // de fallos, sin importar cuantos fallos concurrentes se dispararon de mas.
        resultados.forEach(f -> assertThat(obtenerResultado(f)).isFalse());
        assertThat(codigoVerificacionEmailPort.verificarCodigo(email, codigoReal, maxIntentos))
                .as("el codigo real ya no debe servir: se llego (o se paso) el maximo de "
                        + "intentos fallidos permitidos")
                .isFalse();
    }

    private static boolean obtenerResultado(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado en un intento concurrente", e);
        }
    }

    private static String claveIntentos(String email) {
        return PREFIJO_INTENTOS + email;
    }
}

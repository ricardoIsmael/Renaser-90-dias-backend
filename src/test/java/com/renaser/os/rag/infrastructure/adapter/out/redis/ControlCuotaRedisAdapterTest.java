package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaRenasiaPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra Redis real (Testcontainers): la atomicidad de {@code INCR + TTL} y de
 * {@code liberar} no se puede demostrar con un mock. El limite diario efectivo en los tests
 * es {@code renaser.renasia.limite-diario=25} (src/test/resources/application.yaml).
 *
 * <p>Usa el reloj real del sistema (no se sobreescribe {@code Clock}): alcanza para probar
 * el TTL y la concurrencia sin necesitar controlar "hoy"/medianoche. Lo que si importa es
 * que la clave se derive por el MISMO camino que produccion — ver {@link #claveDeHoy}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ControlCuotaRedisAdapterTest {

    /**
     * Identico al prefijo privado {@code ControlCuotaRedisAdapter.CLAVE_PREFIJO} (mismo
     * archivo, mismo paquete) — se duplica aca porque el campo es {@code private}, no
     * package-private. Si ese literal cambia, este test hay que actualizarlo a mano.
     */
    private static final String CLAVE_PREFIJO = "renasia:cuota:";

    /**
     * Identica a {@code ControlCuotaRedisAdapter.ZONA_PADRON}, y duplicada aca por el mismo
     * motivo que {@link #CLAVE_PREFIJO}: el campo es {@code private}. Si el padron cambia de
     * zona, hay que actualizar las dos.
     */
    private static final ZoneId ZONA_PADRON = ZoneId.of("America/Lima");

    @Autowired
    private ControlCuotaRenasiaPort controlCuotaRenasiaPort;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private Clock clock;

    @Test
    @DisplayName("C-8: el TTL se fija en el primer consumo del dia y NO se renueva en los "
            + "consumos siguientes (si se renovara, la cuota nunca llegaria a medianoche)")
    void elTtlSeFijaUnaSolaVezYNoSeRenuevaEnConsumosPosteriores() throws InterruptedException {
        UserId actorId = UserId.of(UUID.randomUUID());

        assertThat(controlCuotaRenasiaPort.intentarConsumir(actorId)).isTrue();
        Long ttlTrasPrimerConsumo = redisTemplate.getExpire(claveDeHoy(actorId), TimeUnit.MILLISECONDS);
        assertThat(ttlTrasPrimerConsumo).isPositive();

        Thread.sleep(400);

        assertThat(controlCuotaRenasiaPort.intentarConsumir(actorId)).isTrue();
        Long ttlTrasSegundoConsumo = redisTemplate.getExpire(claveDeHoy(actorId), TimeUnit.MILLISECONDS);

        assertThat(ttlTrasSegundoConsumo).isLessThan(ttlTrasPrimerConsumo);
    }

    @Test
    @DisplayName("C-8: una clave envenenada (existe, sin TTL) se autorepara en el siguiente "
            + "consumo, en vez de quedar contando para siempre sin vencer a medianoche")
    void unaClaveEnvenenadaSinTtlSeAutoreparaEnElSiguienteConsumo() {
        UserId actorId = UserId.of(UUID.randomUUID());
        String clave = claveDeHoy(actorId);
        redisTemplate.opsForValue().set(clave, "3");
        assertThat(redisTemplate.getExpire(clave)).isEqualTo(-1L);

        boolean permitido = controlCuotaRenasiaPort.intentarConsumir(actorId);

        assertThat(permitido).isTrue();
        assertThat(redisTemplate.getExpire(clave)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("C-8: liberar() sobre una clave que no existe NO crea una clave nueva en -1 "
            + "sin TTL (antes, un DECR sobre una clave vencida/inexistente la creaba huerfana)")
    void liberarSobreUnaClaveInexistenteNoCreaUnaClaveHuerfana() {
        UserId actorId = UserId.of(UUID.randomUUID());
        String clave = claveDeHoy(actorId);
        assertThat(redisTemplate.hasKey(clave)).isFalse();

        controlCuotaRenasiaPort.liberar(actorId);

        assertThat(redisTemplate.hasKey(clave))
                .as("liberar() sobre una clave inexistente no debe crear ninguna clave nueva")
                .isFalse();
    }

    @Test
    @DisplayName("liberar() sobre una clave existente la decrementa normalmente, sin tocar el TTL")
    void liberarSobreUnaClaveExistenteLaDecrementaSinTocarElTtl() {
        UserId actorId = UserId.of(UUID.randomUUID());
        String clave = claveDeHoy(actorId);
        controlCuotaRenasiaPort.intentarConsumir(actorId);
        controlCuotaRenasiaPort.intentarConsumir(actorId);
        Long ttlAntesDeLiberar = redisTemplate.getExpire(clave, TimeUnit.MILLISECONDS);

        controlCuotaRenasiaPort.liberar(actorId);

        assertThat(redisTemplate.opsForValue().get(clave)).isEqualTo("1");
        assertThat(redisTemplate.getExpire(clave, TimeUnit.MILLISECONDS)).isPositive();
        assertThat(redisTemplate.getExpire(clave, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(ttlAntesDeLiberar);
    }

    @Test
    @DisplayName("el limite diario se respeta bajo consumos concurrentes (INCR + TTL atomicos via Lua)")
    void elLimiteSeRespetaBajoConcurrencia() throws InterruptedException {
        UserId actorId = UserId.of(UUID.randomUUID());
        int limiteDiario = 25; // renaser.renasia.limite-diario en src/test/resources/application.yaml
        int intentosConcurrentes = limiteDiario + 15;

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> resultados;
        try {
            List<Callable<Boolean>> tareas = IntStream.range(0, intentosConcurrentes)
                    .<Callable<Boolean>>mapToObj(i -> () -> controlCuotaRenasiaPort.intentarConsumir(actorId))
                    .toList();
            resultados = pool.invokeAll(tareas, 30, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        long exitosos = resultados.stream().filter(ControlCuotaRedisAdapterTest::obtenerResultado).count();

        assertThat(exitosos).as("nunca deben aceptarse mas de %s consumos por dia para el mismo actor",
                limiteDiario).isEqualTo(limiteDiario);
    }

    private static boolean obtenerResultado(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Fallo inesperado en un consumo concurrente", e);
        }
    }

    /**
     * Deriva la clave exactamente como {@code ControlCuotaRedisAdapter.claveDeHoy}: por el
     * puerto {@code Clock} y en la zona del padron, nunca con un {@code LocalDate.now(...)}
     * armado a mano.
     *
     * <p><b>E-126.</b> Antes usaba {@code LocalDate.now(ZoneOffset.UTC)} mientras el adaptador
     * ya armaba la clave en {@code America/Lima}. Lima es UTC-5, asi que entre las 19:00 y la
     * medianoche de Lima las dos fechas diferian y el test leia una clave que el adaptador
     * nunca habia escrito: TTL {@code -2}/{@code -1} y {@code expected "1" but was null}.
     * Misma familia que E-91, E-105 y E-106 — el codigo estaba bien y era el fixture el que
     * reconstruia a mano un dato que produccion deriva del reloj del padron.
     */
    private String claveDeHoy(UserId actorId) {
        LocalDate hoyDelPadron = clock.now().atZone(ZONA_PADRON).toLocalDate();
        return CLAVE_PREFIJO + actorId.value() + ":" + hoyDelPadron;
    }
}

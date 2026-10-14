package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.rag.application.ports.out.circuito.EstadoCompartidoDelCircuitoPort;
import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.rag.domain.model.circuito.EstadoDelCircuito;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El circuito de la IA en Redis, compartido por todas las instancias (spec 002, plan.md D7):
 * <ul>
 *   <li>{@code renaser:ia:circuito:<parte>}: un hash con el estado y su {@code version}. Se reemplaza con un script
 *       Lua que compara la versión (compare-and-set): no hay {@code WATCH} ni transacción que mantener abierta.</li>
 *   <li>{@code renaser:ia:circuito:<parte>:fallas}: un sorted set con una entrada por falla (puntaje = instante en
 *       milisegundos). Anotar, podar lo viejo y contar la ventana van en un solo script: atómico entre instancias.</li>
 * </ul>
 * Los instantes salen del {@code Clock} de quien llama, nunca del reloj de Redis: así las pruebas mueven el tiempo y
 * las ventanas siguen siendo coherentes. El TTL del sorted set es solo limpieza.
 */
@Component
class EstadoDelCircuitoRedisAdapter implements EstadoCompartidoDelCircuitoPort {

    static final String PREFIJO = "renaser:ia:circuito:";

    /** KEYS[1] el hash; ARGV[1] la versión esperada; ARGV[2..7] versión, estado, aperturas y los tres instantes. */
    private static final RedisScript<Long> REEMPLAZAR_SI_SIGUE_IGUAL = new DefaultRedisScript<>(
            "local actual = redis.call('HGET', KEYS[1], 'version') "
                    + "if not actual then actual = '0' end "
                    + "if actual ~= ARGV[1] then return 0 end "
                    + "redis.call('HSET', KEYS[1], 'version', ARGV[2], 'estado', ARGV[3], 'aperturas', ARGV[4], "
                    + "'abiertoDesde', ARGV[5], 'proximaPrueba', ARGV[6], 'pruebaVence', ARGV[7]) "
                    + "return 1",
            Long.class);

    /** KEYS[1] el sorted set; ARGV[1] ahora (ms); ARGV[2] inicio de la ventana (ms); ARGV[3] la entrada; ARGV[4] TTL. */
    private static final RedisScript<Long> ANOTAR_Y_CONTAR = new DefaultRedisScript<>(
            "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3]) "
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[4]) "
                    + "return redis.call('ZCOUNT', KEYS[1], '(' .. ARGV[2], '+inf')",
            Long.class);

    private static final Duration TTL_MINIMO = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;

    EstadoDelCircuitoRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public CircuitoDeIa leer(ParteDeLaIa parte) {
        Map<Object, Object> campos = redis.opsForHash().entries(clave(parte));
        if (campos.isEmpty()) {
            return CircuitoDeIa.cerrado(parte);
        }
        return new CircuitoDeIa(parte, EstadoDelCircuito.valueOf(texto(campos, "estado")),
                Integer.parseInt(texto(campos, "aperturas")), instante(campos, "abiertoDesde"),
                instante(campos, "proximaPrueba"), instante(campos, "pruebaVence"),
                Long.parseLong(texto(campos, "version")));
    }

    @Override
    public boolean reemplazar(CircuitoDeIa esperado, CircuitoDeIa nuevo) {
        Long resultado = redis.execute(REEMPLAZAR_SI_SIGUE_IGUAL, List.of(clave(nuevo.parte())),
                Long.toString(esperado.version()), Long.toString(nuevo.version()), nuevo.estado().name(),
                Integer.toString(nuevo.aperturasSeguidas()), texto(nuevo.abiertoDesde()),
                texto(nuevo.proximaPrueba()), texto(nuevo.pruebaVence()));
        return resultado != null && resultado == 1L;
    }

    @Override
    public int anotarFalla(ParteDeLaIa parte, Instant ahora, Duration ventana) {
        Duration ttl = ventana.multipliedBy(2).compareTo(TTL_MINIMO) > 0 ? ventana.multipliedBy(2) : TTL_MINIMO;
        Long fallas = redis.execute(ANOTAR_Y_CONTAR, List.of(claveDeFallas(parte)),
                Long.toString(ahora.toEpochMilli()), Long.toString(ahora.minus(ventana).toEpochMilli()),
                UUID.randomUUID().toString(), Long.toString(ttl.toMillis()));
        return fallas == null ? 0 : fallas.intValue();
    }

    @Override
    public int fallasEnVentana(ParteDeLaIa parte, Instant ahora, Duration ventana) {
        Long fallas = redis.opsForZSet().count(claveDeFallas(parte),
                Math.nextUp((double) ahora.minus(ventana).toEpochMilli()), Double.POSITIVE_INFINITY);
        return fallas == null ? 0 : fallas.intValue();
    }

    @Override
    public void olvidarFallas(ParteDeLaIa parte) {
        redis.delete(claveDeFallas(parte));
    }

    static String clave(ParteDeLaIa parte) {
        return PREFIJO + parte.clave();
    }

    static String claveDeFallas(ParteDeLaIa parte) {
        return clave(parte) + ":fallas";
    }

    private static String texto(Map<Object, Object> campos, String nombre) {
        Object valor = campos.get(nombre);
        if (valor == null) {
            throw new IllegalStateException("El circuito guardado en Redis no tiene " + nombre);
        }
        return valor.toString();
    }

    private static Instant instante(Map<Object, Object> campos, String nombre) {
        Object valor = campos.get(nombre);
        return valor == null || valor.toString().isEmpty() ? null : Instant.parse(valor.toString());
    }

    private static String texto(Instant instante) {
        return instante == null ? "" : instante.toString();
    }
}

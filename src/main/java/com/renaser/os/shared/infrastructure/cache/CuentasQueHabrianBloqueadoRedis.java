package com.renaser.os.shared.infrastructure.cache;

import com.renaser.os.shared.application.ports.out.AnotarCuentaQueHabriaBloqueadoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.SujetoDeLimite;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * El registro durable de quién habría sido bloqueado (spec 050, «Cómo funciona solo registrar»): un hash por grupo y día,
 * {@code proteccion:habria:<grupo>:<AAAA-MM-DD>}, con el id de la cuenta como campo y las veces como valor, 30 días de vida. Lo lee
 * {@code scripts/proteccion/cuentas-que-habrian-bloqueado.sh}. El registro de Docker solo vive desde el último despliegue; esto sí
 * sobrevive para decidir los topes de las dos semanas.
 *
 * <p>Solo guarda el id de la cuenta: un pedido sin sesión suma a un campo fijo ({@code sin_cuenta}), nunca a una IP. El día es el de
 * Lima, que es el que el dueño lee; no es una regla del programa, solo una etiqueta de lectura.
 */
@Component
class CuentasQueHabrianBloqueadoRedis implements AnotarCuentaQueHabriaBloqueadoPort {

    static final String PREFIJO = "proteccion:habria:";

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Duration VIDA = Duration.ofDays(30);

    private static final RedisScript<Long> SUMAR_CON_VIDA = new DefaultRedisScript<>(
            "local n = redis.call('HINCRBY', KEYS[1], ARGV[1], 1) "
                    + "if redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end "
                    + "return n",
            Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    CuentasQueHabrianBloqueadoRedis(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void anotar(GrupoDeLimite grupo, SujetoDeLimite sujeto) {
        String dia = clock.now().atZone(LIMA).toLocalDate().toString();
        redis.execute(SUMAR_CON_VIDA, List.of(PREFIJO + grupo.clave() + ":" + dia),
                sujeto.paraElRegistro(), String.valueOf(VIDA.toMillis()));
    }
}

package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.users.application.ports.out.matriz.AnotarDenegacionDeLaMatrizPort;
import com.renaser.os.users.application.ports.out.matriz.DenegacionDeLaMatriz;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * El registro durable de lo que la matriz de MENTOR y TRAINEE_GUIDE negó o habría negado (spec 050, tramo H): un hash por día,
 * {@code proteccion:permisos:denegaciones:<AAAA-MM-DD>}, con el campo {@code <ESTADO> <ROL> <PERMISO> <MÉTODO> <RUTA>} y las veces
 * como valor, 30 días de vida. Lo lee {@code scripts/proteccion/denegaciones-de-la-matriz.sh}. El registro de Docker solo vive desde
 * el último despliegue; esto sí sobrevive a las semanas que dura la sombra.
 *
 * <p>No guarda la cuenta: el campo es el rol y el endpoint, con la plantilla de la ruta (cardinalidad acotada por los handlers). El día
 * es el de Lima, que es el que el dueño lee; no es una regla del programa, solo una etiqueta de lectura.
 */
@Component
class DenegacionesDeLaMatrizRedis implements AnotarDenegacionDeLaMatrizPort {

    static final String PREFIJO = "proteccion:permisos:denegaciones:";

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Duration VIDA = Duration.ofDays(30);

    private static final RedisScript<Long> SUMAR_CON_VIDA = new DefaultRedisScript<>(
            "local n = redis.call('HINCRBY', KEYS[1], ARGV[1], 1) "
                    + "if redis.call('PTTL', KEYS[1]) < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end "
                    + "return n",
            Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    DenegacionesDeLaMatrizRedis(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void anotar(DenegacionDeLaMatriz denegacion) {
        String dia = clock.now().atZone(LIMA).toLocalDate().toString();
        redis.execute(SUMAR_CON_VIDA, List.of(PREFIJO + dia), campoDe(denegacion), String.valueOf(VIDA.toMillis()));
    }

    static String campoDe(DenegacionDeLaMatriz d) {
        return d.estado() + " " + d.rol() + " " + d.permiso() + " " + d.metodo() + " " + d.ruta();
    }
}

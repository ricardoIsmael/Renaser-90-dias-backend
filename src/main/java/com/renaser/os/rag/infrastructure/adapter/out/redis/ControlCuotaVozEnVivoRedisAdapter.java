package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaVozEnVivoPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Segundos de voz en vivo por persona y por dia (D-162). Clave
 * {@code renasia:voz-en-vivo:{usuarioId}:{fecha}}, donde la fecha es el dia LOCAL de la persona
 * (la calcula el caso de uso, regla 02).
 *
 * <p>La suma y el vencimiento van en un solo script Lua, por lo mismo que corrigio C-8 en
 * {@link ControlCuotaRedisAdapter}: dos comandos sueltos dejan la clave sin TTL si el proceso muere
 * entre uno y otro. El TTL es de dos dias y no "hasta la medianoche": como la fecha ya va en la
 * clave, la del dia siguiente es otra, y asi no hace falta saber la zona aca.
 */
@Component
class ControlCuotaVozEnVivoRedisAdapter implements ControlCuotaVozEnVivoPort {

    static final String CLAVE_PREFIJO = "renasia:voz-en-vivo:";
    static final Duration VIGENCIA = Duration.ofDays(2);

    private static final RedisScript<Long> SUMAR_CON_TTL_SI_FALTA = new DefaultRedisScript<>(
            "local total = redis.call('INCRBY', KEYS[1], ARGV[1]) "
                    + "if redis.call('TTL', KEYS[1]) == -1 then "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[2]) "
                    + "end "
                    + "return total",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    ControlCuotaVozEnVivoRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Duration usadoEn(UserId actorId, LocalDate dia) {
        String valor = redisTemplate.opsForValue().get(clave(actorId, dia));
        return valor == null ? Duration.ZERO : Duration.ofSeconds(Long.parseLong(valor));
    }

    @Override
    public Duration sumar(UserId actorId, LocalDate dia, Duration tramo) {
        Long total = redisTemplate.execute(SUMAR_CON_TTL_SI_FALTA, List.of(clave(actorId, dia)),
                String.valueOf(tramo.toSeconds()), String.valueOf(VIGENCIA.toMillis()));
        return total == null ? usadoEn(actorId, dia) : Duration.ofSeconds(total);
    }

    static String clave(UserId actorId, LocalDate dia) {
        return CLAVE_PREFIJO + actorId.value() + ":" + dia;
    }
}

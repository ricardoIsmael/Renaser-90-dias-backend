package com.renaser.os.notifications.infrastructure.adapter.out.redis;

import com.renaser.os.notifications.application.ports.out.limite.LimiteDeAvisosPorHoraPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El tope de avisos por hora en Redis: un sorted set por grupo con una entrada por aviso que SALIÓ (puntaje = su
 * instante). Ver si ya estaba, podar lo de hace más de dos horas, contar los que salieron en la hora que termina en el
 * aviso y anotarlo si entra van en un solo script, atómico entre instancias. Los omitidos no ocupan lugar: con un
 * circuito que oscila sin parar, siguen saliendo 4 por hora y no 4 y después silencio. Un aviso que ya salió vuelve a
 * dar «sí» (la reentrega del outbox).
 *
 * <p>Si Redis no responde, deja pasar el aviso (y lo anota): mejor un push de más a Soporte TI que ninguno.
 */
@Component
class LimiteDeAvisosPorHoraRedisAdapter implements LimiteDeAvisosPorHoraPort {

    private static final Logger log = LoggerFactory.getLogger(LimiteDeAvisosPorHoraRedisAdapter.class);
    static final String PREFIJO = "renaser:notificaciones:limite:";
    private static final Duration HORA = Duration.ofHours(1);
    private static final Duration CONSERVAR = Duration.ofHours(2);

    /**
     * KEYS[1] el set; ARGV[1] el instante (ms); ARGV[2] el id; ARGV[3] inicio de la hora (ms); ARGV[4] poda (ms);
     * ARGV[5] TTL (ms); ARGV[6] el máximo. Devuelve 1 si sale, 0 si se omite.
     */
    private static final RedisScript<Long> ADMITIR_SI_ENTRA = new DefaultRedisScript<>(
            "if redis.call('ZSCORE', KEYS[1], ARGV[2]) then return 1 end "
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. ARGV[4]) "
                    + "if redis.call('ZCOUNT', KEYS[1], '(' .. ARGV[3], ARGV[1]) >= tonumber(ARGV[6]) then return 0 end "
                    + "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]) "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[5]) "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redis;

    LimiteDeAvisosPorHoraRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean admite(String grupo, UUID id, Instant en, int maximo) {
        try {
            Long sale = redis.execute(ADMITIR_SI_ENTRA, List.of(PREFIJO + grupo),
                    Long.toString(en.toEpochMilli()), id.toString(), Long.toString(en.minus(HORA).toEpochMilli()),
                    Long.toString(en.minus(CONSERVAR).toEpochMilli()), Long.toString(CONSERVAR.toMillis()),
                    Integer.toString(maximo));
            return sale == null || sale == 1L;
        } catch (RuntimeException e) {
            log.warn("[notifications] no se pudo consultar el tope de avisos de {}; el aviso sale igual ({})", grupo,
                    e.getClass().getSimpleName());
            return true;
        }
    }
}

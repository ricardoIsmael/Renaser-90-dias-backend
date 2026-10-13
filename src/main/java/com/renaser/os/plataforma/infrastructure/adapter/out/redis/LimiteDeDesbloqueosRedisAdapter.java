package com.renaser.os.plataforma.infrastructure.adapter.out.redis;

import com.renaser.os.plataforma.application.ports.out.LimitarDesbloqueosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Cuenta las contraseñas mal escritas en la consola, en Redis para que el límite valga entre instancias (como
 * {@code users.LimitarSolicitudesResetRedisAdapter}). El incremento y el TTL van en un mismo script (C-8): la clave
 * nunca queda sin vencimiento. El TTL se fija en el primer fallo y se vuelve a fijar al llegar al máximo, así el
 * bloqueo dura la ventana entera desde el último fallo que lo provocó.
 */
@Component
class LimiteDeDesbloqueosRedisAdapter implements LimitarDesbloqueosPort {

    private static final String CLAVE = "soporte-ti:desbloqueo:fallos:";
    private static final RedisScript<Long> REGISTRAR_FALLO = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
                    + "if redis.call('TTL', KEYS[1]) == -1 or n >= tonumber(ARGV[2]) then "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[1]) end return n", Long.class);

    private final StringRedisTemplate redis;

    LimiteDeDesbloqueosRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean bloqueado(UserId actor, int maximo) {
        String valor = redis.opsForValue().get(CLAVE + actor.value());
        return valor != null && Long.parseLong(valor) >= maximo;
    }

    @Override
    public void registrarFallo(UserId actor, Duration ventana, int maximo) {
        redis.execute(REGISTRAR_FALLO, List.of(CLAVE + actor.value()), Long.toString(ventana.toMillis()),
                Integer.toString(maximo));
    }
}

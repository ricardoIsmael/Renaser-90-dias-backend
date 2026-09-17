package com.renaser.os.chat.infrastructure.adapter.out.redis;

import com.renaser.os.chat.application.ports.out.presencia.PresenciaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * La presencia, en Redis, como una llave por persona con vencimiento.
 *
 * <p>Una llave suelta y no un conjunto (`SET`) a proposito: un conjunto no vence por elemento,
 * asi que una instancia que muere sin poder limpiar dejaria a su gente dentro del conjunto
 * para siempre. Con una llave por usuario, el vencimiento hace de red de seguridad sin que
 * nadie tenga que barrer nada.
 */
@Component
class RedisPresenciaAdapter implements PresenciaPort {

    private static final String PREFIJO = "chat:presencia:";

    private final StringRedisTemplate redisTemplate;

    RedisPresenciaAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void marcarEnLinea(UserId usuarioId, Duration vigencia) {
        redisTemplate.opsForValue().set(llave(usuarioId), "1", vigencia);
    }

    @Override
    public void marcarFueraDeLinea(UserId usuarioId) {
        redisTemplate.delete(llave(usuarioId));
    }

    @Override
    public Set<UserId> enLineaDe(Collection<UserId> candidatos) {
        if (candidatos == null || candidatos.isEmpty()) {
            return Set.of();
        }
        List<UserId> orden = List.copyOf(candidatos);
        // Un solo MGET para toda la lista: el roster de un grupo son diez personas y una ida a
        // Redis por cada una seria N+1 en la pantalla que mas se abre.
        List<String> valores = redisTemplate.opsForValue()
                .multiGet(orden.stream().map(RedisPresenciaAdapter::llave).toList());
        if (valores == null) {
            return Set.of();
        }
        Set<UserId> enLinea = new LinkedHashSet<>();
        for (int i = 0; i < orden.size() && i < valores.size(); i++) {
            if (valores.get(i) != null) {
                enLinea.add(orden.get(i));
            }
        }
        return enLinea;
    }

    private static String llave(UserId usuarioId) {
        return PREFIJO + usuarioId.value();
    }
}

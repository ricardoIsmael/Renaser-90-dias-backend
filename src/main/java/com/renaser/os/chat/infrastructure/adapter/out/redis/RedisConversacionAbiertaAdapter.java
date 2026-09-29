package com.renaser.os.chat.infrastructure.adapter.out.redis;

import com.renaser.os.chat.application.ports.out.presencia.ConversacionAbiertaPort;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Una llave por (conversación, persona) con vencimiento, por el mismo motivo que
 * {@link RedisPresenciaAdapter}: un conjunto no vence por elemento, y una instancia que muere sin
 * limpiar dejaría a su gente «mirando el chat» para siempre, sin avisos.
 */
@Component
class RedisConversacionAbiertaAdapter implements ConversacionAbiertaPort {

    private static final String PREFIJO = "chat:abierta:";

    private final StringRedisTemplate redisTemplate;

    RedisConversacionAbiertaAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void marcarAbierta(UserId usuarioId, ConversacionId conversacionId, Duration vigencia) {
        redisTemplate.opsForValue().set(llave(conversacionId, usuarioId), "1", vigencia);
    }

    @Override
    public void marcarCerrada(UserId usuarioId, ConversacionId conversacionId) {
        redisTemplate.delete(llave(conversacionId, usuarioId));
    }

    @Override
    public Set<UserId> laTienenAbierta(ConversacionId conversacionId, Collection<UserId> candidatos) {
        if (candidatos == null || candidatos.isEmpty()) {
            return Set.of();
        }
        List<UserId> orden = List.copyOf(candidatos);
        List<String> valores = redisTemplate.opsForValue()
                .multiGet(orden.stream().map(u -> llave(conversacionId, u)).toList());
        if (valores == null) {
            return Set.of();
        }
        Set<UserId> abiertas = new LinkedHashSet<>();
        for (int i = 0; i < orden.size() && i < valores.size(); i++) {
            if (valores.get(i) != null) {
                abiertas.add(orden.get(i));
            }
        }
        return abiertas;
    }

    private static String llave(ConversacionId conversacionId, UserId usuarioId) {
        return PREFIJO + conversacionId.value() + ":" + usuarioId.value();
    }
}

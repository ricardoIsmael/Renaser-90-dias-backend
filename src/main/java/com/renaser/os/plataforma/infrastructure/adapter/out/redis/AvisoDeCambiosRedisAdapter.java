package com.renaser.os.plataforma.infrastructure.adapter.out.redis;

import com.renaser.os.plataforma.application.ports.out.AvisarCambioDeEstadosPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica la versión nueva en {@link CanalDeFuncionalidades#NOMBRE}. Fire-and-forget, como el fanout del chat: el
 * cambio ya está en Postgres, y si Redis no responde cada instancia se pone al día en su recarga de cada 30 s.
 */
@Component
class AvisoDeCambiosRedisAdapter implements AvisarCambioDeEstadosPort {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeCambiosRedisAdapter.class);

    private final StringRedisTemplate redis;

    AvisoDeCambiosRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void avisar(long version) {
        try {
            redis.convertAndSend(CanalDeFuncionalidades.NOMBRE, Long.toString(version));
        } catch (RuntimeException e) {
            log.warn("[plataforma] no se pudo avisar la version {} por Redis; las demas instancias se pondran al dia "
                    + "en su recarga", version, e);
        }
    }
}

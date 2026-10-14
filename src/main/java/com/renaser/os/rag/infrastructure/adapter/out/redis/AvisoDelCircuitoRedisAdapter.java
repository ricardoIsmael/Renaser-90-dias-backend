package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.rag.application.ports.out.circuito.AvisarCambioDelCircuitoPort;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica en {@link CanalDelCircuito#NOMBRE} qué parte cambió. Fire-and-forget, como el aviso de los interruptores
 * manuales: si Redis no responde, cada instancia se pone al día en su recarga periódica.
 */
@Component
class AvisoDelCircuitoRedisAdapter implements AvisarCambioDelCircuitoPort {

    private static final Logger log = LoggerFactory.getLogger(AvisoDelCircuitoRedisAdapter.class);

    private final StringRedisTemplate redis;

    AvisoDelCircuitoRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void avisar(ParteDeLaIa parte) {
        try {
            redis.convertAndSend(CanalDelCircuito.NOMBRE, parte.clave());
        } catch (RuntimeException e) {
            log.warn("[rag.circuito] no se pudo avisar el cambio de {} por Redis; las demás instancias lo verán en "
                    + "su recarga ({})", parte.clave(), e.getClass().getSimpleName());
        }
    }
}

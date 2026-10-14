package com.renaser.os.plataforma.infrastructure.adapter.out.redis;

import com.renaser.os.plataforma.application.ports.out.AvisarCambioDeRolesPersonalizadosPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica en {@link CanalDeRolesPersonalizados#NOMBRE} que algo cambió. Fire-and-forget, como el aviso de los
 * interruptores: el cambio ya está en Postgres, y si Redis no responde cada instancia se pone al día en 30 s.
 */
@Component
class AvisoDeRolesPersonalizadosRedisAdapter implements AvisarCambioDeRolesPersonalizadosPort {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeRolesPersonalizadosRedisAdapter.class);

    private final StringRedisTemplate redis;

    AvisoDeRolesPersonalizadosRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void avisar() {
        try {
            redis.convertAndSend(CanalDeRolesPersonalizados.NOMBRE, "cambio");
        } catch (RuntimeException e) {
            log.warn("[plataforma] no se pudo avisar el cambio de roles personalizados por Redis; las demas "
                    + "instancias se pondran al dia en su recarga", e);
        }
    }
}

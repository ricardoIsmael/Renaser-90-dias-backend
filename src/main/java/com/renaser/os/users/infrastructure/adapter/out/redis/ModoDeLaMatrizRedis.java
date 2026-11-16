package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatriz;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatrizFijadoPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * El modo de la matriz de MENTOR y TRAINEE_GUIDE en Redis (spec 050, tramo H): la clave {@code proteccion:permisos:modo} con el
 * valor {@code SOMBRA} o {@code APLICAR}. Cambiarlo es un {@code SET} (lo hace {@code scripts/despliegue/permisos-modo.sh}) y el
 * interceptor lo ve en 30 s sin reiniciar. Quitar la clave devuelve la matriz al modo de la configuración.
 *
 * <p>Un valor que no es un modo (un error de tipeo) se ignora con un aviso: rige el de la configuración, que es el que no niega a
 * nadie por accidente mientras no se encienda a propósito.
 */
@Component
class ModoDeLaMatrizRedis implements ModoDeLaMatrizFijadoPort {

    static final String CLAVE = "proteccion:permisos:modo";

    private static final Logger log = LoggerFactory.getLogger(ModoDeLaMatrizRedis.class);

    private final StringRedisTemplate redis;

    ModoDeLaMatrizRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<ModoDeLaMatriz> fijado() {
        String valor = redis.opsForValue().get(CLAVE);
        Optional<ModoDeLaMatriz> modo = ModoDeLaMatriz.interpretar(valor);
        if (modo.isEmpty() && valor != null && !valor.isBlank()) {
            log.warn("El modo guardado para la matriz de mentores no es SOMBRA ni APLICAR: se usa el de la configuración");
        }
        return modo;
    }
}

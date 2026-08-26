package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Mismo patron que {@code ControlCuotaRedisAdapter} (rag, D-48): {@code INCR} atomico contra
 * Redis, con el TTL fijado solo la primera vez que la clave llega a 1 — asi la ventana no se
 * reinicia en cada intento nuevo dentro del mismo periodo.
 */
@Component
class LimitarSolicitudesResetRedisAdapter implements LimitarSolicitudesResetPort {

    private static final String CLAVE_PREFIJO = "reset-password:rl:";

    private final StringRedisTemplate redisTemplate;

    LimitarSolicitudesResetRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean registrarIntento(String clave, Duration ventana, int maximo) {
        String claveRedis = CLAVE_PREFIJO + clave;
        Long intentos = redisTemplate.opsForValue().increment(claveRedis);
        if (intentos == null) {
            // Fallo inesperado del cliente de Redis: el limite es proteccion contra abuso, no
            // fuente de verdad de negocio — no se bloquea el reseteo de contrasena por esto.
            return true;
        }
        if (intentos == 1L) {
            redisTemplate.expire(claveRedis, ventana);
        }
        return intentos <= maximo;
    }
}

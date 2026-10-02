package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.eliminacion.CodigoEliminarCuentaPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Codigo para eliminar la cuenta (D-243), bajo {@code eliminar-cuenta:codigo:*} /
 * {@code eliminar-cuenta:intentos:*}: espacio de claves propio, distinto del alta y del reset. La
 * mecanica es la compartida de {@link AlmacenCodigoNumericoRedis}.
 */
@Component
class CodigoEliminarCuentaRedisAdapter implements CodigoEliminarCuentaPort {

    private static final String PREFIJO_CODIGO = "eliminar-cuenta:codigo:";
    private static final String PREFIJO_INTENTOS = "eliminar-cuenta:intentos:";

    private final AlmacenCodigoNumericoRedis almacen;

    CodigoEliminarCuentaRedisAdapter(StringRedisTemplate redisTemplate) {
        this.almacen = new AlmacenCodigoNumericoRedis(redisTemplate, PREFIJO_CODIGO, PREFIJO_INTENTOS);
    }

    @Override
    public String generarCodigo(String email, Duration vigencia) {
        return almacen.generarCodigo(email, vigencia);
    }

    @Override
    public boolean verificarCodigo(String email, String codigo, int maxIntentos) {
        return almacen.verificarCodigo(email, codigo, maxIntentos);
    }
}

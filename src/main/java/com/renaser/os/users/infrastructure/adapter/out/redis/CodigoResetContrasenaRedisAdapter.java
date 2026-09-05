package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.CodigoResetContrasenaPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Codigo de 6 digitos de RECUPERACION DE CONTRASENA (2026-09-04, D-102), bajo
 * {@code reset-password:codigo:*} / {@code reset-password:intentos:*} — el mismo namespace
 * que el token ({@code reset-password:{token}}) y el rate limit ({@code reset-password:rl:*})
 * del reset. Un token es Base64 URL-safe, sin dos puntos, asi que no puede colisionar con
 * {@code codigo:} ni {@code intentos:}.
 *
 * <p>Espacio de claves DISTINTO al del alta a proposito (ver javadoc de
 * {@code CodigoResetContrasenaPort}). La mecanica es identica y compartida:
 * {@link AlmacenCodigoNumericoRedis}.
 */
@Component
class CodigoResetContrasenaRedisAdapter implements CodigoResetContrasenaPort {

    private static final String PREFIJO_CODIGO = "reset-password:codigo:";
    private static final String PREFIJO_INTENTOS = "reset-password:intentos:";

    private final AlmacenCodigoNumericoRedis almacen;

    CodigoResetContrasenaRedisAdapter(StringRedisTemplate redisTemplate) {
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

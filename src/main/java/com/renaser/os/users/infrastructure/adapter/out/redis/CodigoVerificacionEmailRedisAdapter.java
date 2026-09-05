package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Codigo de 6 digitos del ALTA, bajo {@code email-verification:*}. Toda la mecanica (codigo +
 * contador de intentos, Lua atomico, un solo uso) vive en {@link AlmacenCodigoNumericoRedis};
 * este adaptador solo fija el espacio de claves. Antes de D-102 (2026-09-04) la implementacion
 * completa estaba aca; se extrajo para compartirla con
 * {@link CodigoResetContrasenaRedisAdapter} sin copiarla.
 */
@Component
class CodigoVerificacionEmailRedisAdapter implements CodigoVerificacionEmailPort {

    private static final String PREFIJO_CODIGO = "email-verification:codigo:";
    private static final String PREFIJO_INTENTOS = "email-verification:intentos:";

    private final AlmacenCodigoNumericoRedis almacen;

    CodigoVerificacionEmailRedisAdapter(StringRedisTemplate redisTemplate) {
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

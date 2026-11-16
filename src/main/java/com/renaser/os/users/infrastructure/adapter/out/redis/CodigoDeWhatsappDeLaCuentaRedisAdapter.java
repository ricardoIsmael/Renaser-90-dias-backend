package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.whatsapp.CodigoDeWhatsappDeLaCuentaPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Codigo para confirmar el WhatsApp de una cuenta (spec 051), bajo {@code whatsapp-cuenta:codigo:*} / {@code whatsapp-cuenta:intentos:*}: espacio
 * de claves propio. La mecanica es la compartida de {@link AlmacenCodigoNumericoRedis}. La clave es {@code cuenta|numero}: ni la cuenta ni el
 * numero pueden contener {@code |}, asi que la clave se parte de una sola forma.
 */
@Component
class CodigoDeWhatsappDeLaCuentaRedisAdapter implements CodigoDeWhatsappDeLaCuentaPort {

    private static final String PREFIJO_CODIGO = "whatsapp-cuenta:codigo:";
    private static final String PREFIJO_INTENTOS = "whatsapp-cuenta:intentos:";

    private final AlmacenCodigoNumericoRedis almacen;

    CodigoDeWhatsappDeLaCuentaRedisAdapter(StringRedisTemplate redisTemplate) {
        this.almacen = new AlmacenCodigoNumericoRedis(redisTemplate, PREFIJO_CODIGO, PREFIJO_INTENTOS);
    }

    @Override
    public String generarCodigo(UserId cuenta, String numero, Duration vigencia) {
        return almacen.generarCodigo(clave(cuenta, numero), vigencia);
    }

    @Override
    public boolean verificarCodigo(UserId cuenta, String numero, String codigo, int maxIntentos) {
        return almacen.verificarCodigo(clave(cuenta, numero), codigo, maxIntentos);
    }

    private static String clave(UserId cuenta, String numero) {
        return cuenta.value() + "|" + numero;
    }
}

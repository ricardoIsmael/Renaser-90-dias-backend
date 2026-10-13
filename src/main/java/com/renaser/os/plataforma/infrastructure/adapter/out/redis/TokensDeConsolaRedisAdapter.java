package com.renaser.os.plataforma.infrastructure.adapter.out.redis;

import com.renaser.os.plataforma.application.ports.out.TokensDeConsolaPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Los tokens de la consola Soporte TI en Redis con TTL (plan.md D6). La clave es el SHA-256 del token —quien lea
 * Redis no obtiene un token usable— y el valor, la persona y la sesión que lo pidieron.
 *
 * <ul>
 *   <li>Consola: vive {@code vigencia} desde el último uso (cada validación renueva el TTL): se bloquea por
 *       inactividad.</li>
 *   <li>Reautenticación: se lee y se borra en un mismo script (un solo uso, aunque lleguen dos pedidos a la vez).</li>
 * </ul>
 */
@Component
class TokensDeConsolaRedisAdapter implements TokensDeConsolaPort {

    private static final String CONSOLA = "soporte-ti:consola:";
    private static final String REAUTENTICACION = "soporte-ti:reautenticacion:";
    private static final RedisScript<String> LEER_Y_BORRAR = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) if v then redis.call('DEL', KEYS[1]) end return v", String.class);

    private final StringRedisTemplate redis;
    private final SecureRandom azar = new SecureRandom();

    TokensDeConsolaRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String emitirDeConsola(UserId actor, String sesion, Duration vigencia) {
        return emitir(CONSOLA, actor, sesion, vigencia);
    }

    @Override
    public String emitirDeReautenticacion(UserId actor, String sesion, Duration vigencia) {
        return emitir(REAUTENTICACION, actor, sesion, vigencia);
    }

    @Override
    public boolean consolaVigente(String token, UserId actor, String sesion, Duration vigencia) {
        String clave = CONSOLA + huella(token);
        if (!dueno(actor, sesion).equals(redis.opsForValue().get(clave))) {
            return false;
        }
        redis.expire(clave, vigencia);
        return true;
    }

    @Override
    public boolean gastarReautenticacion(String token, UserId actor, String sesion) {
        String guardado = redis.execute(LEER_Y_BORRAR, List.of(REAUTENTICACION + huella(token)));
        return dueno(actor, sesion).equals(guardado);
    }

    private String emitir(String prefijo, UserId actor, String sesion, Duration vigencia) {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(prefijo + huella(token), dueno(actor, sesion), vigencia);
        return token;
    }

    private static String dueno(UserId actor, String sesion) {
        return actor.value() + "|" + (sesion == null ? "-" : sesion);
    }

    private static String huella(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

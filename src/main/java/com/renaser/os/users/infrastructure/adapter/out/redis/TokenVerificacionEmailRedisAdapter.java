package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/** Clave {@code email-verified:{tokenOpaco}} -> valor: el email verificado. Mismo patron
 * exacto que {@link TokenResetContrasenaRedisAdapter} (GETDEL atomico, 256 bits de entropia). */
@Component
class TokenVerificacionEmailRedisAdapter implements TokenVerificacionEmailPort {

    private static final String CLAVE_PREFIJO = "email-verified:";
    private static final int BYTES_ALEATORIOS = 32;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    TokenVerificacionEmailRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generar(String email, Duration vigencia) {
        String token = tokenAleatorio();
        redisTemplate.opsForValue().set(clave(token), email, vigencia);
        return token;
    }

    @Override
    public Optional<String> consumir(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(clave(token)));
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[BYTES_ALEATORIOS];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String clave(String token) {
        return CLAVE_PREFIJO + token;
    }
}

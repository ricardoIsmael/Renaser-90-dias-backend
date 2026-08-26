package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Clave {@code reset-password:{tokenOpaco}} -> valor: el {@link UserId} como string. TTL nativo
 * de Redis (docs/MODULO_AUTH.md §2.2): sin cron de purga, sin tabla que crezca.
 */
@Component
class TokenResetContrasenaRedisAdapter implements TokenResetContrasenaPort {

    private static final String CLAVE_PREFIJO = "reset-password:";

    /** 256 bits de entropia — muy por encima de lo necesario para que adivinar un token sea inviable. */
    private static final int BYTES_ALEATORIOS = 32;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    TokenResetContrasenaRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generar(UserId usuarioId, Duration vigencia) {
        String token = tokenAleatorio();
        redisTemplate.opsForValue().set(clave(token), usuarioId.value().toString(), vigencia);
        return token;
    }

    /**
     * {@code getAndDelete} emite {@code GETDEL}: lectura y borrado en un solo viaje atomico a
     * Redis. El "un solo uso" no depende de un GET seguido de un DEL desde este lado — eso si
     * dejaria una ventana donde dos requests casi simultaneas con el mismo token podrian las dos
     * leer "exito" antes de que cualquiera borre la clave.
     */
    @Override
    public Optional<UserId> consumir(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String usuarioIdCrudo = redisTemplate.opsForValue().getAndDelete(clave(token));
        return Optional.ofNullable(usuarioIdCrudo).map(UserId::of);
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

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
        if (!esTokenBienFormado(token)) {
            return Optional.empty();
        }
        String usuarioIdCrudo = redisTemplate.opsForValue().getAndDelete(clave(token));
        if (usuarioIdCrudo == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UserId.of(usuarioIdCrudo));
        } catch (IllegalArgumentException e) {
            /* El valor guardado no es un UUID. Se devuelve vacio en vez de dejar propagar la
               excepcion: `UserId.of` mete el VALOR en el mensaje y `GlobalExceptionHandler` lo
               devuelve tal cual en el cuerpo del 400 — o sea que un valor leido de Redis terminaba
               impreso en la respuesta. No deberia poder pasar nunca; si pasa, es un dato corrupto,
               no algo que contarle a quien llama. */
            return Optional.empty();
        }
    }

    private String tokenAleatorio() {
        byte[] bytes = new byte[BYTES_ALEATORIOS];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String clave(String token) {
        return CLAVE_PREFIJO + token;
    }

    /**
     * Que el token tenga la FORMA del que emite {@link #generar}, antes de tocar Redis.
     *
     * <p><b>El agujero que cierra (2026-09-18), y es toma de cuenta.</b> El campo solo estaba
     * anotado {@code @NotBlank}, y la clave se arma concatenando: {@code "reset-password:" + token}.
     * Bajo ESE MISMO prefijo viven el codigo OTP ({@code reset-password:codigo:<email>}), su
     * contador de intentos ({@code reset-password:intentos:<email>}) y todos los limites de tasa
     * ({@code reset-password:rl:...}). Como {@code consumir} hace {@code GETDEL}, un
     * {@code POST /api/v1/auth/password/reset-confirm} —publico, sin sesion— con
     * {@code token: "intentos:<email de la victima>"} BORRABA el contador de intentos.
     *
     * <p>Con el contador borrado, el limite de 5 intentos del OTP no se alcanza nunca y el codigo de
     * 6 digitos queda expuesto a fuerza bruta ilimitada: acertarlo devuelve el token de reset real,
     * con el que se fija una contrasena nueva. Cualquier cuenta con login por contrasena.
     *
     * <p>La comprobacion de forma alcanza para cerrarlo entero: el token es Base64URL sin relleno,
     * que nunca contiene {@code ':'}, asi que ninguna de las otras familias de claves es
     * direccionable. Se eligio esto y no cambiar el prefijo porque un prefijo nuevo dejaria
     * huerfanos los tokens ya emitidos y cortaria las recuperaciones en curso.
     */
    private static boolean esTokenBienFormado(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return token.chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '-' || c == '_');
    }
}

package com.renaser.os.users.infrastructure.adapter.out.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

/**
 * Codigo numerico de un solo uso + contador de intentos, en dos claves de Redis bajo un prefijo
 * propio. Es la implementacion que antes vivia entera dentro de
 * {@link CodigoVerificacionEmailRedisAdapter}; se extrajo (2026-09-04, D-102) cuando el reset de
 * contrasena por codigo necesito exactamente el mismo comportamiento bajo OTRO espacio de claves.
 * Los dos adaptadores son ahora una linea cada uno: eligen el prefijo y delegan aca. No es un
 * {@code @Component}: cada adaptador construye el suyo con su prefijo.
 *
 * <p>{@code intentos} nunca sobrevive a {@code codigo}: se borra explicito al generar uno
 * nuevo, y su TTL se iguala al que le queda a {@code codigo} en el primer fallo.
 *
 * <p><b>Corregido (C-8, docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b>
 * {@code verificarCodigo} leia el codigo y despues lo borraba en comandos separados; dos
 * solicitudes simultaneas podian validar el mismo codigo correcto. Ahora la comparacion, el
 * consumo, el incremento de intentos y la copia del TTL corren dentro de un unico script Lua,
 * atomico de punta a punta y con auto-reparacion si una clave de intentos ya quedo envenenada.
 */
final class AlmacenCodigoNumericoRedis {

    /** 6 digitos, cero-rellenado — mismo formato que espera la pantalla de la app. */
    private static final int DIGITOS = 6;

    /**
     * {@code KEYS[1]}: codigo. {@code KEYS[2]}: intentos. {@code ARGV[1]}: codigo recibido.
     * {@code ARGV[2]}: maximo de intentos. El resultado es 1 si el codigo se consumio y 0 en
     * cualquier otro caso.
     */
    private static final RedisScript<Long> VERIFICAR_Y_CONSUMIR = new DefaultRedisScript<>(
            "local guardado = redis.call('GET', KEYS[1]) "
                    + "if not guardado then "
                    + "return 0 "
                    + "end "
                    + "if guardado == ARGV[1] then "
                    + "redis.call('DEL', KEYS[1]) "
                    + "redis.call('DEL', KEYS[2]) "
                    + "return 1 "
                    + "end "
                    + "local intentos = redis.call('INCR', KEYS[2]) "
                    + "if redis.call('TTL', KEYS[2]) == -1 then "
                    + "local ttlCodigo = redis.call('TTL', KEYS[1]) "
                    + "if ttlCodigo > 0 then "
                    + "redis.call('EXPIRE', KEYS[2], ttlCodigo) "
                    + "end "
                    + "end "
                    + "if intentos >= tonumber(ARGV[2]) then "
                    + "redis.call('DEL', KEYS[1]) "
                    + "redis.call('DEL', KEYS[2]) "
                    + "end "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String prefijoCodigo;
    private final String prefijoIntentos;
    private final SecureRandom secureRandom = new SecureRandom();

    AlmacenCodigoNumericoRedis(StringRedisTemplate redisTemplate, String prefijoCodigo, String prefijoIntentos) {
        this.redisTemplate = redisTemplate;
        this.prefijoCodigo = prefijoCodigo;
        this.prefijoIntentos = prefijoIntentos;
    }

    String generarCodigo(String email, Duration vigencia) {
        String codigo = codigoAleatorio();
        // Se borra ANTES de fijar el nuevo codigo: un codigo recien pedido arranca en 0
        // intentos, nunca hereda el contador de uno anterior ya vencido.
        redisTemplate.delete(claveIntentos(email));
        redisTemplate.opsForValue().set(claveCodigo(email), codigo, vigencia);
        return codigo;
    }

    boolean verificarCodigo(String email, String codigo, int maxIntentos) {
        Long consumido = redisTemplate.execute(VERIFICAR_Y_CONSUMIR,
                List.of(claveCodigo(email), claveIntentos(email)), codigo, String.valueOf(maxIntentos));
        return consumido != null && consumido == 1L;
    }

    private String codigoAleatorio() {
        int valor = secureRandom.nextInt((int) Math.pow(10, DIGITOS));
        return String.format("%0" + DIGITOS + "d", valor);
    }

    private String claveCodigo(String email) {
        return prefijoCodigo + email;
    }

    private String claveIntentos(String email) {
        return prefijoIntentos + email;
    }
}

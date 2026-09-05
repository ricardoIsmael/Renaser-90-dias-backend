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
 * {@code registrarIntentoFallido} hacia {@code INCR} y DESPUES leia+copiaba el TTL de
 * {@code codigo} en comandos separados; si el proceso moria entre uno y otro, la clave de
 * intentos quedaba SIN TTL. Ahora ambos pasos corren dentro de un unico script Lua
 * ({@code INCR} + copiar el TTL de {@code codigo} SOLO si {@code intentos} todavia no tiene uno
 * propio), atomico de punta a punta y con auto-reparacion si una clave ya quedo envenenada.
 */
final class AlmacenCodigoNumericoRedis {

    /** 6 digitos, cero-rellenado — mismo formato que espera la pantalla de la app. */
    private static final int DIGITOS = 6;

    /**
     * {@code KEYS[1]}: la clave del codigo. {@code KEYS[2]}: la clave de intentos.
     * Incrementa {@code intentos} de forma atomica y, SOLO si esa clave todavia no tiene
     * TTL, le copia el TTL restante de {@code codigo} (si {@code codigo} ya no tiene uno
     * vivo — no deberia pasar, porque el llamador ya confirmo que existe antes de invocar
     * esto — no se fija nada, para no dejar un TTL inventado).
     */
    private static final RedisScript<Long> INCREMENTAR_INTENTOS_CON_TTL_DEL_CODIGO = new DefaultRedisScript<>(
            "local actual = redis.call('INCR', KEYS[2]) "
                    + "if redis.call('TTL', KEYS[2]) == -1 then "
                    + "local ttlCodigo = redis.call('TTL', KEYS[1]) "
                    + "if ttlCodigo > 0 then "
                    + "redis.call('EXPIRE', KEYS[2], ttlCodigo) "
                    + "end "
                    + "end "
                    + "return actual",
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
        String claveCodigo = claveCodigo(email);
        String guardado = redisTemplate.opsForValue().get(claveCodigo);
        if (guardado == null) {
            return false;
        }
        if (guardado.equals(codigo)) {
            redisTemplate.delete(claveCodigo);
            redisTemplate.delete(claveIntentos(email));
            return true;
        }
        registrarIntentoFallido(email, claveCodigo, maxIntentos);
        return false;
    }

    private void registrarIntentoFallido(String email, String claveCodigo, int maxIntentos) {
        String claveIntentos = claveIntentos(email);
        Long intentos = redisTemplate.execute(INCREMENTAR_INTENTOS_CON_TTL_DEL_CODIGO,
                List.of(claveCodigo, claveIntentos));
        if (intentos == null) {
            return;
        }
        if (intentos >= maxIntentos) {
            // Se agotaron los intentos: se invalida el codigo entero (no solo se deja de
            // aceptar) para forzar pedir uno nuevo, en vez de dejarlo "vivo" hasta que venza
            // el TTL mientras alguien lo sigue adivinando.
            redisTemplate.delete(claveCodigo);
            redisTemplate.delete(claveIntentos);
        }
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

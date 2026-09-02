package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

/**
 * Codigo + contador de intentos en dos claves separadas. {@code intentos} nunca sobrevive a
 * {@code codigo}: se borra explicito al generar uno nuevo, y su TTL se iguala al que le
 * queda a {@code codigo} en el primer fallo.
 *
 * <p><b>Corregido (C-8, docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b>
 * la version anterior de este adaptador evitaba a proposito un script Lua para este caso
 * ("el riesgo es, como mucho, dejar pasar un intento de mas") — pero el hallazgo C-8 mostro
 * que el riesgo real no era ese: {@code registrarIntentoFallido} hacia {@code INCR} y
 * DESPUES leia+copiaba el TTL de {@code codigo} en comandos separados; si el proceso moria
 * (o el segundo comando fallaba) entre uno y otro, la clave de intentos quedaba SIN TTL.
 * Ahora ambos pasos corren dentro de un unico script Lua ({@code INCR} + copiar el TTL de
 * {@code codigo} SOLO si {@code intentos} todavia no tiene uno propio), atomico de punta a
 * punta y con auto-reparacion si una clave ya quedo envenenada por el codigo viejo.
 */
@Component
class CodigoVerificacionEmailRedisAdapter implements CodigoVerificacionEmailPort {

    private static final String PREFIJO_CODIGO = "email-verification:codigo:";
    private static final String PREFIJO_INTENTOS = "email-verification:intentos:";

    /** 6 digitos, cero-rellenado — mismo formato que espera la pantalla de la app (un solo uso). */
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
    private final SecureRandom secureRandom = new SecureRandom();

    CodigoVerificacionEmailRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generarCodigo(String email, Duration vigencia) {
        String codigo = codigoAleatorio();
        // Se borra ANTES de fijar el nuevo codigo: un codigo recien pedido arranca en 0
        // intentos, nunca hereda el contador de uno anterior ya vencido.
        redisTemplate.delete(claveIntentos(email));
        redisTemplate.opsForValue().set(claveCodigo(email), codigo, vigencia);
        return codigo;
    }

    @Override
    public boolean verificarCodigo(String email, String codigo, int maxIntentos) {
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
        return PREFIJO_CODIGO + email;
    }

    private String claveIntentos(String email) {
        return PREFIJO_INTENTOS + email;
    }
}

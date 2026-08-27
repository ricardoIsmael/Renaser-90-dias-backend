package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Codigo + contador de intentos en dos claves separadas que se mantienen sincronizadas a mano
 * (sin script Lua: el codebase no usa scripting de Redis en ningun otro lado y el riesgo de una
 * carrera aca es, como mucho, dejar pasar un intento de mas bajo concurrencia extrema — no
 * filtrar el codigo). {@code intentos} nunca sobrevive a {@code codigo}: se borra explicito al
 * generar uno nuevo, y su TTL se iguala al que le queda a {@code codigo} en el primer fallo.
 */
@Component
class CodigoVerificacionEmailRedisAdapter implements CodigoVerificacionEmailPort {

    private static final String PREFIJO_CODIGO = "email-verification:codigo:";
    private static final String PREFIJO_INTENTOS = "email-verification:intentos:";

    /** 6 digitos, cero-rellenado — mismo formato que espera la pantalla de la app (un solo uso). */
    private static final int DIGITOS = 6;

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
        Long intentos = redisTemplate.opsForValue().increment(claveIntentos);
        if (intentos == null) {
            return;
        }
        if (intentos == 1L) {
            Long ttlCodigoSegundos = redisTemplate.getExpire(claveCodigo);
            if (ttlCodigoSegundos != null && ttlCodigoSegundos > 0) {
                redisTemplate.expire(claveIntentos, Duration.ofSeconds(ttlCodigoSegundos));
            }
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

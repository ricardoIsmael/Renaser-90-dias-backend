package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Mismo patron que {@code ControlCuotaRedisAdapter} (rag, D-48): contador atomico contra
 * Redis, con el TTL fijado solo mientras la clave no tenga uno — asi la ventana no se
 * reinicia en cada intento nuevo dentro del mismo periodo.
 *
 * <p><b>Corregido (C-8, docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b>
 * antes, {@code INCR} y {@code EXPIRE} eran dos comandos separados. Si el proceso moria (o
 * Redis rechazaba el segundo comando) justo entre uno y otro, la clave quedaba SIN TTL para
 * siempre: un contador que nunca se reinicia y que a partir de ahi bloquea, de forma
 * permanente, el reseteo de contrasena (y desde C-16/E-72, tambien el alta de cuentas
 * nuevas) de esa IP o email. Ahora el incremento y el TTL corren dentro del MISMO script
 * Lua: Redis ejecuta un script completo de punta a punta sin que otro comando pueda
 * intercalarse, asi que no existe un estado intermedio observable donde el contador ya
 * subio pero el TTL todavia no se aplico. Ademas, el script chequea {@code TTL == -1}
 * (no solo "es la primera vez que llega a 1") para auto-repararse: si una clave quedo
 * envenenada por el codigo viejo, la SIGUIENTE llamada que la toque le fija el TTL que le
 * faltaba, sin necesidad de limpieza manual.
 */
@Component
class LimitarSolicitudesResetRedisAdapter implements LimitarSolicitudesResetPort {

    private static final String CLAVE_PREFIJO = "reset-password:rl:";

    /**
     * {@code KEYS[1]}: la clave del contador. {@code ARGV[1]}: la ventana, en milisegundos.
     * Incrementa de forma atomica y, SOLO si la clave todavia no tiene TTL ({@code -1}),
     * le fija el de la ventana con {@code PEXPIRE}. Nunca renueva el TTL de una clave que ya
     * lo tiene: si lo hiciera, el limite dejaria de ser "N por ventana" y pasaria a ser
     * "N intentos seguidos sin pausa", que es una regla de negocio distinta y no la que
     * pide este puerto.
     */
    private static final RedisScript<Long> INCREMENTAR_CON_TTL_SI_FALTA = new DefaultRedisScript<>(
            "local actual = redis.call('INCR', KEYS[1]) "
                    + "if redis.call('TTL', KEYS[1]) == -1 then "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[1]) "
                    + "end "
                    + "return actual",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    LimitarSolicitudesResetRedisAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean registrarIntento(String clave, Duration ventana, int maximo) {
        String claveRedis = CLAVE_PREFIJO + clave;
        Long intentos = redisTemplate.execute(INCREMENTAR_CON_TTL_SI_FALTA, List.of(claveRedis),
                String.valueOf(ventana.toMillis()));
        if (intentos == null) {
            // Fallo inesperado del cliente de Redis: el limite es proteccion contra abuso, no
            // fuente de verdad de negocio — no se bloquea el reseteo de contrasena por esto.
            return true;
        }
        return intentos <= maximo;
    }
}

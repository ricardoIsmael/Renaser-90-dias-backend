package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaRenasiaPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Contador diario en Redis, D-48 (docs/MODULO_RAG.md §3). Clave
 * {@code renasia:cuota:{usuarioId}:{fecha}}, TTL hasta medianoche UTC — la BD esta
 * congelada y no tiene columna de contador, y Redis ya esta en el stack (lo usa
 * {@code chat} para el fanout).
 *
 * <p>Incremento atomico via {@code INCR}; el TTL se fija solo la primera vez (cuando el
 * contador queda en 1 tras el incremento) para no reiniciar la ventana en cada mensaje.
 */
@Component
class ControlCuotaRedisAdapter implements ControlCuotaRenasiaPort {

    private static final String CLAVE_PREFIJO = "renasia:cuota:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final int limiteDiario;

    ControlCuotaRedisAdapter(StringRedisTemplate redisTemplate, Clock clock,
                              @Value("${renaser.renasia.limite-diario}") int limiteDiario) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.limiteDiario = limiteDiario;
    }

    @Override
    public boolean intentarConsumir(UserId actorId) {
        String clave = claveDeHoy(actorId);
        Long consumidos = redisTemplate.opsForValue().increment(clave);
        if (consumidos == null) {
            // Fallo inesperado del cliente de Redis: no bloqueamos a Renasia por esto, el
            // limite es una proteccion de abuso, no una fuente de verdad de negocio.
            return true;
        }
        if (consumidos == 1L) {
            redisTemplate.expire(clave, segundosHastaMedianoche());
        }
        return consumidos <= limiteDiario;
    }

    @Override
    public void liberar(UserId actorId) {
        redisTemplate.opsForValue().decrement(claveDeHoy(actorId));
    }

    private String claveDeHoy(UserId actorId) {
        return CLAVE_PREFIJO + actorId.value() + ":" + clock.today();
    }

    private Duration segundosHastaMedianoche() {
        Instant medianoche = clock.today().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Duration restante = Duration.between(clock.now(), medianoche);
        return restante.isNegative() ? Duration.ofSeconds(1) : restante;
    }
}

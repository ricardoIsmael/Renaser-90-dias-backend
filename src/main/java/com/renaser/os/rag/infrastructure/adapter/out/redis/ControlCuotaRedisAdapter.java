package com.renaser.os.rag.infrastructure.adapter.out.redis;

import com.renaser.os.rag.application.ports.out.cuota.ControlCuotaRenasiaPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Contador diario en Redis, D-48 (docs/MODULO_RAG.md §3). Clave
 * {@code renasia:cuota:{usuarioId}:{fecha}}, TTL hasta medianoche UTC — la BD esta
 * congelada y no tiene columna de contador, y Redis ya esta en el stack (lo usa
 * {@code chat} para el fanout).
 *
 * <p><b>Corregido (C-8, docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html):</b>
 * {@code INCR} y {@code EXPIRE} corrian como dos comandos separados; si el proceso moria
 * entre uno y otro la clave quedaba sin TTL para siempre. Ahora ambos corren dentro del
 * mismo script Lua (ver javadoc de {@code LimitarSolicitudesResetRedisAdapter}, mismo
 * defecto y misma correccion, con auto-reparacion si la clave ya quedo envenenada). Ademas,
 * {@link #liberar} ahora solo decrementa si la clave TODAVIA existe: antes, un {@code DECR}
 * sobre una clave ya vencida (medianoche de por medio) o inexistente creaba una clave nueva
 * en {@code -1} SIN TTL — quedaba huerfana para siempre, porque la clave del dia siguiente
 * usa una fecha distinta y nada vuelve a tocar esa clave vieja.
 */
@Component
class ControlCuotaRedisAdapter implements ControlCuotaRenasiaPort {

    private static final String CLAVE_PREFIJO = "renasia:cuota:";

    /**
     * {@code KEYS[1]}: la clave del contador diario. {@code ARGV[1]}: milisegundos hasta
     * medianoche. Mismo criterio que {@code LimitarSolicitudesResetRedisAdapter}: solo fija
     * TTL si la clave todavia no tiene uno, para no renovar la ventana en cada mensaje.
     */
    private static final RedisScript<Long> INCREMENTAR_CON_TTL_SI_FALTA = new DefaultRedisScript<>(
            "local actual = redis.call('INCR', KEYS[1]) "
                    + "if redis.call('TTL', KEYS[1]) == -1 then "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[1]) "
                    + "end "
                    + "return actual",
            Long.class);

    /** {@code KEYS[1]}: la clave del contador diario. Nunca crea la clave si no existe. */
    private static final RedisScript<Long> DECREMENTAR_SI_EXISTE = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then "
                    + "return redis.call('DECR', KEYS[1]) "
                    + "end "
                    + "return 0",
            Long.class);

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
        Long consumidos = redisTemplate.execute(INCREMENTAR_CON_TTL_SI_FALTA, List.of(clave),
                String.valueOf(segundosHastaMedianoche().toMillis()));
        if (consumidos == null) {
            // Fallo inesperado del cliente de Redis: no bloqueamos a Renasia por esto, el
            // limite es una proteccion de abuso, no una fuente de verdad de negocio.
            return true;
        }
        return consumidos <= limiteDiario;
    }

    @Override
    public void liberar(UserId actorId) {
        redisTemplate.execute(DECREMENTAR_SI_EXISTE, List.of(claveDeHoy(actorId)));
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

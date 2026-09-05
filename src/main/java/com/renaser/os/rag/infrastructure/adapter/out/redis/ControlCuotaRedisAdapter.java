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
import java.time.LocalDate;
import java.time.ZoneId;
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

    /** Zona del padron. Igual que `ParticipacionPrograma.ZONA_POR_DEFECTO`: hoy todos en Lima. */
    private static final ZoneId ZONA_PADRON = ZoneId.of("America/Lima");

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

    /**
     * El dia de la cuota es el del PADRON, no el del servidor.
     *
     * <p>Antes la clave se armaba con {@code clock.today()} y el vencimiento se calculaba contra la
     * medianoche UTC. Con el backend en UTC y el padron en Lima (UTC-5) eso significaba que la
     * cuota diaria se renovaba a las 19:00 hora local: quien la agotaba a la tarde la recuperaba
     * entera esa misma noche, y el "dia" de la cuota no coincidia con el dia que la persona ve en
     * la app. Misma familia que E-105.
     */
    private String claveDeHoy(UserId actorId) {
        return CLAVE_PREFIJO + actorId.value() + ":" + hoyDelPadron();
    }

    private LocalDate hoyDelPadron() {
        return clock.now().atZone(ZONA_PADRON).toLocalDate();
    }

    /** Lo que falta para la medianoche DEL PADRON, que es cuando la cuota se renueva de verdad. */
    private Duration segundosHastaMedianoche() {
        Instant medianoche = hoyDelPadron().plusDays(1).atStartOfDay(ZONA_PADRON).toInstant();
        Duration restante = Duration.between(clock.now(), medianoche);
        return restante.isNegative() ? Duration.ofSeconds(1) : restante;
    }
}

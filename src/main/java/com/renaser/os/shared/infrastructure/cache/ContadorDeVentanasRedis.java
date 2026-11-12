package com.renaser.os.shared.infrastructure.cache;

import com.renaser.os.shared.domain.ContadorDeVentanas;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.SujetoDeLimite;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * El contador del límite por cuenta en Redis (spec 050, FR-012): ventana fija, un script Lua que mira todas las ventanas antes de
 * contar en ninguna y el mismo patrón de vencimiento del contador de los límites de autenticación
 * ({@code LimitarSolicitudesResetRedisAdapter}, C-8): el TTL se fija con {@code PEXPIRE} solo mientras la clave no tenga uno, en el
 * mismo script que el {@code INCR}, así que no existe un estado en que el contador subió y el vencimiento todavía no.
 *
 * <p>Claves: {@code prot:lim:<grupo>:<ventana>:<sujeto>} ({@code prot:lim:muro:1m:c:3f2a…}).
 *
 * <p><b>Qué cuenta el script.</b> Recorre las ventanas y, si alguna ya está en su tope, cuenta <i>solo</i> en esa (para medir cuánto se
 * pasó: el uso sigue subiendo por encima de 1) y devuelve su {@code PTTL} para el {@code Retry-After}; las demás no se tocan, así que un
 * rechazo de la ventana del minuto no gasta la de la hora. Si todas tienen margen, cuenta en todas.
 */
@Component
class ContadorDeVentanasRedis implements ContadorDeVentanas {

    private static final String PREFIJO = "prot:lim:";

    /**
     * {@code KEYS[i]}: la clave de la ventana i. {@code ARGV[2i-1]}: su duración en ms; {@code ARGV[2i]}: su tope.
     * Devuelve {@code {1, 0, 0, usado1, usado2…}} si pasó y {@code {0, i, pttl, usado}} si la ventana i ya estaba en su tope.
     */
    private static final RedisScript<List> CONTAR = new DefaultRedisScript<>(
            "local n = #KEYS "
                    + "for i = 1, n do "
                    + "  local actual = tonumber(redis.call('GET', KEYS[i]) or '0') "
                    + "  if actual >= tonumber(ARGV[2 * i]) then "
                    + "    local usado = redis.call('INCR', KEYS[i]) "
                    + "    if redis.call('PTTL', KEYS[i]) < 0 then redis.call('PEXPIRE', KEYS[i], ARGV[2 * i - 1]) end "
                    + "    return {0, i, redis.call('PTTL', KEYS[i]), usado} "
                    + "  end "
                    + "end "
                    + "local usos = {1, 0, 0} "
                    + "for i = 1, n do "
                    + "  usos[#usos + 1] = redis.call('INCR', KEYS[i]) "
                    + "  if redis.call('PTTL', KEYS[i]) < 0 then redis.call('PEXPIRE', KEYS[i], ARGV[2 * i - 1]) end "
                    + "end "
                    + "return usos",
            List.class);

    private final StringRedisTemplate redis;

    ContadorDeVentanasRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Resultado intentar(GrupoDeLimite grupo, SujetoDeLimite sujeto, List<Ventana> ventanas) {
        List<String> claves = new ArrayList<>(ventanas.size());
        List<String> argumentos = new ArrayList<>(ventanas.size() * 2);
        for (Ventana ventana : ventanas) {
            claves.add(PREFIJO + grupo.clave() + ":" + ventana.nombre() + ":" + sujeto.clave());
            argumentos.add(String.valueOf(ventana.duracion().toMillis()));
            argumentos.add(String.valueOf(ventana.maximo()));
        }
        List<?> respuesta = redis.execute(CONTAR, claves, argumentos.toArray());
        if (respuesta == null || respuesta.size() < 4) {
            throw new IllegalStateException("Redis devolvió una respuesta vacía al contar");
        }
        return aResultado(respuesta, ventanas);
    }

    private static Resultado aResultado(List<?> respuesta, List<Ventana> ventanas) {
        boolean paso = numero(respuesta, 0) == 1;
        if (!paso) {
            Ventana pasada = ventanas.get((int) numero(respuesta, 1) - 1);
            Uso uso = new Uso(pasada.nombre(), numero(respuesta, 3), pasada.maximo());
            return new Resultado(false, pasada.nombre(), Math.max(0, numero(respuesta, 2)), List.of(uso));
        }
        List<Uso> usos = new ArrayList<>(ventanas.size());
        for (int i = 0; i < ventanas.size(); i++) {
            usos.add(new Uso(ventanas.get(i).nombre(), numero(respuesta, 3 + i), ventanas.get(i).maximo()));
        }
        return new Resultado(true, "", 0, usos);
    }

    private static long numero(List<?> respuesta, int posicion) {
        return ((Number) respuesta.get(posicion)).longValue();
    }
}

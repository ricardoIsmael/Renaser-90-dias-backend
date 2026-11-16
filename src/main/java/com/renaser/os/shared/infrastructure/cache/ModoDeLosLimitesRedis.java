package com.renaser.os.shared.infrastructure.cache;

import com.renaser.os.shared.application.ports.out.ModoDeLosLimitesPort;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * El modo de cada grupo en Redis (spec 050, Q6): la clave {@code proteccion:modo:<grupo>} con el valor {@code DESACTIVADO},
 * {@code REGISTRAR} o {@code APLICAR}. Cambiarlo es un {@code SET} (lo hace {@code scripts/despliegue/limites-modo.sh}) y el filtro lo
 * ve en {@code cache-del-modo} (30 s) sin reiniciar. Quitar la clave devuelve el grupo al modo de la configuración.
 *
 * <p>Un valor que no es un modo (un error de tipeo) se ignora con un aviso: el grupo sigue con el de la configuración, que es el
 * que no bloquea a nadie por accidente.
 */
@Component
class ModoDeLosLimitesRedis implements ModoDeLosLimitesPort {

    static final String PREFIJO = "proteccion:modo:";

    private static final Logger log = LoggerFactory.getLogger(ModoDeLosLimitesRedis.class);
    private static final GrupoDeLimite[] GRUPOS = GrupoDeLimite.values();

    private final StringRedisTemplate redis;

    ModoDeLosLimitesRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Map<GrupoDeLimite, ModoDeLimite> fijados() {
        List<String> claves = Arrays.stream(GRUPOS).map(grupo -> PREFIJO + grupo.clave()).toList();
        List<String> valores = redis.opsForValue().multiGet(claves);
        Map<GrupoDeLimite, ModoDeLimite> fijados = new EnumMap<>(GrupoDeLimite.class);
        for (int i = 0; valores != null && i < GRUPOS.length; i++) {
            interpretar(GRUPOS[i], valores.get(i), fijados);
        }
        return fijados;
    }

    private static void interpretar(GrupoDeLimite grupo, String valor, Map<GrupoDeLimite, ModoDeLimite> fijados) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        try {
            fijados.put(grupo, ModoDeLimite.valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException noEsUnModo) {
            log.warn("El modo guardado para el grupo {} no es DESACTIVADO, REGISTRAR ni APLICAR: se usa el de la configuración", grupo.clave());
        }
    }
}

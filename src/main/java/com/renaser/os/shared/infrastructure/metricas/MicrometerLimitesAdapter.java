package com.renaser.os.shared.infrastructure.metricas;

import com.renaser.os.shared.application.ports.out.RegistrarMetricaDeLimitesPort;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ResultadoDelLimite;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las métricas del límite por cuenta en Prometheus (spec 050, FR-019): {@code renaser_limite_pedidos_total{grupo,resultado}}, con
 * {@code resultado} permitido, habria_bloqueado, bloqueado o error_de_redis, y {@code renaser_limite_uso{grupo,ventana}}, la
 * distribución de lo contado sobre el tope (1,0 es justo el tope; más de 1, que se pasó) en cubetas fijas para sacar p50, p95 y p99
 * con {@code histogram_quantile}. Las etiquetas son cerradas (diez grupos, cuatro resultados, tres ventanas): ninguna lleva una
 * cuenta, una IP ni una ruta.
 */
@Component
class MicrometerLimitesAdapter implements RegistrarMetricaDeLimitesPort {

    private static final Logger log = LoggerFactory.getLogger(MicrometerLimitesAdapter.class);
    private static final double[] CUBETAS_DE_USO = {0.1, 0.25, 0.5, 0.75, 0.9, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0};

    private final MeterRegistry registro;
    private final Map<GrupoDeLimite, Map<ResultadoDelLimite, Counter>> pedidos = new EnumMap<>(GrupoDeLimite.class);
    private final Map<String, DistributionSummary> usos = new ConcurrentHashMap<>();

    MicrometerLimitesAdapter(MeterRegistry registro) {
        this.registro = registro;
        for (GrupoDeLimite grupo : GrupoDeLimite.values()) {
            Map<ResultadoDelLimite, Counter> porResultado = new EnumMap<>(ResultadoDelLimite.class);
            for (ResultadoDelLimite resultado : ResultadoDelLimite.values()) {
                porResultado.put(resultado, Counter.builder("renaser.limite.pedidos")
                        .description("Pedidos contados por el límite por cuenta, por grupo y resultado")
                        .tag("grupo", grupo.clave()).tag("resultado", resultado.etiqueta()).register(registro));
            }
            pedidos.put(grupo, porResultado);
        }
    }

    @Override
    public void pedido(GrupoDeLimite grupo, ResultadoDelLimite resultado) {
        medir(() -> pedidos.get(grupo).get(resultado).increment());
    }

    @Override
    public void uso(GrupoDeLimite grupo, String ventana, double proporcion) {
        medir(() -> usos.computeIfAbsent(grupo.clave() + "|" + ventana, clave -> DistributionSummary
                .builder("renaser.limite.uso").description("Lo contado sobre el tope de cada ventana")
                .tag("grupo", grupo.clave()).tag("ventana", ventana).serviceLevelObjectives(CUBETAS_DE_USO)
                .register(registro)).record(proporcion));
    }

    /** Medir nunca rompe un pedido: una métrica perdida no puede costar una respuesta. */
    private static void medir(Runnable medicion) {
        try {
            medicion.run();
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar una métrica del límite por cuenta ({})", e.getClass().getSimpleName());
        }
    }
}

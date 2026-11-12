package com.renaser.os.shared.infrastructure.metricas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ResultadoDelLimite;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las métricas del límite por cuenta y su panel (spec 050, FR-019): los nombres que de verdad salen en {@code /actuator/prometheus} son
 * los que el panel de Grafana consulta, y {@code config.alloy} no los descarta antes de mandarlos a Grafana Cloud.
 */
class MetricasDelLimitePorCuentaTest {

    private static final Path PANEL = Path.of("infra/observabilidad/grafana/dashboards/renaser-proteccion.json");
    private static final Path ALLOY = Path.of("infra/observabilidad/alloy/config.alloy");

    // spec 050 FR-019
    @Test
    @DisplayName("Prometheus expone renaser_limite_pedidos_total{grupo,resultado} y renaser_limite_uso_bucket{grupo,ventana}, sin cuentas ni rutas en las etiquetas")
    void losNombresQueSalenEnPrometheus() {
        PrometheusMeterRegistry registro = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MicrometerLimitesAdapter metricas = new MicrometerLimitesAdapter(registro);

        metricas.pedido(GrupoDeLimite.MURO, ResultadoDelLimite.HABRIA_BLOQUEADO);
        metricas.pedido(GrupoDeLimite.IA_FUERA_DE_SER, ResultadoDelLimite.ERROR_DE_REDIS);
        metricas.uso(GrupoDeLimite.MURO, "1m", 1.2);
        metricas.uso(GrupoDeLimite.MURO, "1m", 0.3);
        String salida = registro.scrape();

        assertThat(salida).contains("renaser_limite_pedidos_total{grupo=\"muro\",resultado=\"habria_bloqueado\"} 1.0")
                .contains("renaser_limite_pedidos_total{grupo=\"ia-fuera-de-ser\",resultado=\"error_de_redis\"} 1.0")
                .contains("renaser_limite_pedidos_total{grupo=\"webhook-whatsapp\",resultado=\"permitido\"} 0.0")
                .contains("renaser_limite_uso_bucket{grupo=\"muro\",ventana=\"1m\",le=\"1.0\"} 1")
                .contains("renaser_limite_uso_bucket{grupo=\"muro\",ventana=\"1m\",le=\"2.0\"} 2")
                .contains("renaser_limite_uso_count{grupo=\"muro\",ventana=\"1m\"} 2");
        assertThat(salida.lines().filter(l -> l.startsWith("renaser_limite_")).filter(l -> l.contains("cuenta=") || l.contains("ruta=")))
                .as("ninguna etiqueta lleva una cuenta ni una ruta").isEmpty();
    }

    // spec 050 FR-019
    @Test
    @DisplayName("el panel de Protección es un JSON válido que consulta las dos métricas, y config.alloy no las descarta")
    void elPanelConsultaLasMetricasYAlloyLasDejaPasar() throws IOException {
        JsonNode panel = new ObjectMapper().readTree(Files.readString(PANEL));

        assertThat(panel.get("uid").asText()).isEqualTo("renaser-proteccion");
        List<String> consultas = new ArrayList<>();
        panel.get("panels").forEach(p -> p.path("targets").forEach(t -> consultas.add(t.get("expr").asText())));
        assertThat(consultas).isNotEmpty().allMatch(c -> c.contains("renaser_limite_pedidos_total") || c.contains("renaser_limite_uso_bucket"));
        assertThat(consultas).anyMatch(c -> c.contains("resultado=\"habria_bloqueado\""))
                .anyMatch(c -> c.contains("histogram_quantile(0.99"))
                .anyMatch(c -> c.contains("histogram_quantile(0.5"))
                .anyMatch(c -> c.contains("histogram_quantile(0.95"));

        String alloy = Files.readString(ALLOY);
        assertThat(alloy).as("config.alloy descarta una lista cerrada de familias; renaser_limite_* no está entre ellas")
                .doesNotContain("renaser_limite");
    }
}

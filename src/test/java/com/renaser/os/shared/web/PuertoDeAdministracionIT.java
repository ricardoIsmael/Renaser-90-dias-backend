package com.renaser.os.shared.web;

import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-237: las metricas viven solo en el puerto de administracion, que en produccion no se publica.
 * Contra el Tomcat real y con la configuracion real ({@code actuator.yaml}, que importan main y
 * test): en el puerto de la aplicacion —el que ve internet por CloudFront y nginx— solo responde
 * el health, en su ruta de siempre; {@code /actuator/prometheus} y los endpoints peligrosos dan 404.
 *
 * <p>{@code @AutoConfigureMetrics}: en las pruebas Boot apaga los exportadores de metricas (y con
 * ellos el endpoint {@code prometheus}) salvo que se pida; sin esto la prueba veria un 404 que en
 * produccion no existe.
 */
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PuertoDeAdministracionIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int puertoPublico;
    @LocalManagementPort
    private int puertoDeAdministracion;

    @Test
    @DisplayName("son dos puertos distintos")
    void dosPuertos() {
        assertThat(puertoDeAdministracion).isPositive().isNotEqualTo(puertoPublico);
    }

    @Test
    @DisplayName("el puerto publico NO sirve /actuator/prometheus ni nada mas de actuator")
    void prometheusNoEstaEnElPuertoPublico() throws Exception {
        for (String ruta : new String[] {"/actuator/prometheus", "/actuator/env", "/actuator/heapdump",
                "/actuator/loggers", "/actuator/metrics", "/actuator", "/prometheus"}) {
            assertThat(get(puertoPublico, ruta).statusCode()).as(ruta).isIn(403, 404);
        }
    }

    @Test
    @DisplayName("el puerto publico sigue respondiendo /actuator/health (CD, despliegue sin corte) y /salud")
    void healthSigueEnElPuertoPublico() throws Exception {
        for (String ruta : new String[] {"/actuator/health", "/salud"}) {
            HttpResponse<String> respuesta = get(puertoPublico, ruta);
            assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
            // Lo que busca desplegar-backend.sh, y sin detalles de los componentes.
            assertThat(respuesta.body()).as(ruta).contains("\"status\":\"UP\"").doesNotContain("components");
        }
    }

    @Test
    @DisplayName("el puerto de administracion sirve prometheus con las etiquetas comunes, y health")
    void prometheusEnElPuertoDeAdministracion() throws Exception {
        HttpResponse<String> metricas = get(puertoDeAdministracion, "/actuator/prometheus");

        assertThat(metricas.statusCode()).isEqualTo(200);
        assertThat(metricas.body())
                .contains("jvm_memory_used_bytes")
                .contains("hikaricp_connections_active")
                .contains("application=\"renaser-backend\"")
                .contains("entorno=\"local\"");
        assertThat(get(puertoDeAdministracion, "/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("ni siquiera el puerto de administracion expone env, heapdump ni loggers")
    void administracionSinEndpointsPeligrosos() throws Exception {
        for (String ruta : new String[] {"/actuator/env", "/actuator/heapdump", "/actuator/loggers",
                "/actuator/configprops", "/actuator/threaddump"}) {
            assertThat(get(puertoDeAdministracion, ruta).statusCode()).as(ruta).isEqualTo(404);
        }
    }

    private HttpResponse<String> get(int puerto, String ruta) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
    }
}

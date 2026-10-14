package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.application.services.CopiaDeDespliegues;
import com.renaser.os.plataforma.application.services.MetricasDeDespliegue;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las métricas del despliegue de punta a punta (contrato fase 2 §3.3 y §7): los pedidos reales pasan por el interceptor,
 * se cuentan dentro (la probadora) y fuera (otra persona, con su 404), se escriben en lote sumando en Postgres (V104) y
 * la consola los devuelve en las 24 h. Un 5xx se registra directo en el servicio: ningún endpoint da un 500 a pedido
 * (que el interceptor marque como error un 5xx lo prueba {@code InterruptoresInterceptorTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MetricasDeDespliegueIT {

    private static final String DESPLIEGUE = "/api/v1/soporte-ti/funcionalidades/chat.grupos/despliegue";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeDespliegues copiaDeDespliegues;
    @Autowired
    private MetricasDeDespliegue metricas;

    private PedidosDePrueba pedidos;
    private String soporte;
    private String consola;
    private final Instant inicio = Instant.now();

    @BeforeEach
    void armar() throws Exception {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        soporte = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        consola = pedidos.desbloquear(soporte);
        metricas.escribirPendientes();
        jdbc.update("DELETE FROM renaser.funcionalidades_metricas_hora");
    }

    @AfterEach
    void limpiar() throws InterruptedException {
        pedidos.esperarElOutbox(inicio);
        jdbc.update("DELETE FROM renaser.funcionalidades_despliegue");
        copiaDeDespliegues.recargar();
        metricas.escribirPendientes();
        jdbc.update("DELETE FROM renaser.funcionalidades_metricas_hora");
        pedidos.borrarCuentas();
    }

    @Test
    @DisplayName("dentro (la probadora) y fuera (otra persona, con su 404) por separado, 5xx como error, lote que suma")
    void dentroFueraYLote() throws Exception {
        UUID probadoraId = pedidos.usuario("APRENDIZ");
        String probadora = pedidos.sesionDe(probadoraId);
        String otra = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));
        empezarAlCeroCon(probadoraId);

        for (int i = 0; i < 3; i++) {
            assertThat(pedidos.enviar("GET", "/api/v1/me/cells", probadora, null).statusCode()).isEqualTo(200);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(pedidos.enviar("GET", "/api/v1/me/cells", otra, null).statusCode()).isEqualTo(404);
        }
        metricas.registrar("chat.grupos", UserId.of(probadoraId), true);
        metricas.escribirPendientes();

        assertThat(totales()).isEqualTo(new Totales(4, 1, 2, 0));

        pedidos.enviar("GET", "/api/v1/me/cells", probadora, null);
        metricas.escribirPendientes();

        assertThat(totales().dentroPedidos()).as("el segundo lote suma, no reemplaza").isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_metricas_hora "
                + "WHERE clave = 'chat.grupos'", Integer.class)).as("una fila por hora y por dentro/fuera")
                .isBetween(2, 4);
    }

    @Test
    @DisplayName("la consola devuelve 24 horas, la última la actual; la purga borra lo de más de 48 h y deja lo nuevo")
    void veinticuatroHorasYPurga() throws Exception {
        Instant estaHora = Instant.now().truncatedTo(ChronoUnit.HOURS);
        insertar(estaHora.minus(Duration.ofHours(49)), 7);
        insertar(estaHora.minus(Duration.ofHours(30)), 5);
        insertar(estaHora.minus(Duration.ofHours(2)), 3);

        HttpResponse<String> respuesta = pedidos.enviar("GET", DESPLIEGUE, soporte, null,
                Map.of("X-Consola-Token", consola));
        JsonNode serie = pedidos.json(respuesta).get("metricas");
        int borradas = metricas.purgarViejas();

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(pedidos.json(respuesta).get("enDespliegue").asBoolean())
                .as("las métricas se ven también sin despliegue").isFalse();
        assertThat(serie).hasSize(24);
        assertThat(Instant.parse(serie.get(23).get("hora").asText())).isEqualTo(estaHora);
        assertThat(serie.get(21).get("dentro").get("pedidos").asLong()).isEqualTo(3);
        assertThat(serie.get(0).get("fuera").get("pedidos").asLong()).isZero();
        assertThat(borradas).as("la de hace 49 h (si la purga programada no llegó antes)").isBetween(0, 1);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(pedidos), 0) FROM renaser.funcionalidades_metricas_hora",
                Long.class)).isEqualTo(8);
    }

    private void empezarAlCeroCon(UUID probadora) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("porcentaje", 0);
        cuerpo.put("motivo", "Medir el despliegue");
        Map<String, String> cabeceras = Map.of("X-Consola-Token", consola,
                "X-Reautenticacion", pedidos.reautenticar(soporte, consola));
        assertThat(pedidos.enviar("PUT", DESPLIEGUE, soporte, cuerpo, cabeceras).statusCode()).isEqualTo(200);
        assertThat(pedidos.enviar("PUT", DESPLIEGUE + "/probadores/" + probadora, soporte, null,
                Map.of("X-Consola-Token", consola)).statusCode()).isEqualTo(200);
    }

    /** Suma las 24 h del {@code GET} (una hora puede partirse en dos si la prueba cruza la hora en punto). */
    private Totales totales() throws Exception {
        HttpResponse<String> respuesta = pedidos.enviar("GET", DESPLIEGUE, soporte, null,
                Map.of("X-Consola-Token", consola));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        long dp = 0;
        long de = 0;
        long fp = 0;
        long fe = 0;
        for (JsonNode hora : pedidos.json(respuesta).get("metricas")) {
            dp += hora.get("dentro").get("pedidos").asLong();
            de += hora.get("dentro").get("errores").asLong();
            fp += hora.get("fuera").get("pedidos").asLong();
            fe += hora.get("fuera").get("errores").asLong();
        }
        return new Totales(dp, de, fp, fe);
    }

    private record Totales(long dentroPedidos, long dentroErrores, long fueraPedidos, long fueraErrores) {
    }

    private void insertar(Instant hora, long pedidosDentro) {
        jdbc.update("INSERT INTO renaser.funcionalidades_metricas_hora (clave, hora, dentro, pedidos, errores) "
                + "VALUES ('chat.grupos', ?, true, ?, 0)", Timestamp.from(hora), pedidosDentro);
    }
}

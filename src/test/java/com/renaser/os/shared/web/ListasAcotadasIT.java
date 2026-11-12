package com.renaser.os.shared.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.PedidosDePrueba;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los topes de las listas y la descarga del diagnóstico de punta a punta (spec 050, tramo F; FR-032 y FR-034), contra el
 * Tomcat, el Postgres y el Redis reales: lo que pasa de su máximo es un 400 con el motivo (como
 * {@code GET /admin/pagos/cuotas}), no una consulta sin tope; y el guard de la descarga pasa antes del primer byte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ListasAcotadasIT {

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private PedidosDePrueba pedidos;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
    }

    @AfterEach
    void limpiar() {
        pedidos.borrarCuentas();
    }

    private HttpResponse<String> get(String ruta, String sesion) throws Exception {
        return pedidos.enviar("GET", ruta, sesion, null);
    }

    private static void esUn400ConElMotivo(HttpResponse<String> respuesta, String motivo) {
        assertThat(respuesta.statusCode()).as(respuesta.body()).isEqualTo(400);
        assertThat(respuesta.body()).contains("\"message\":\"" + motivo + "\"");
    }

    // spec 050 FR-034
    @Test
    @DisplayName("Personas del programa: size=101, 100000, 0 y negativo son 400 con el motivo; la página negativa también; size=100 pasa")
    void personasDelPrograma() throws Exception {
        String lider = pedidos.sesionDe(pedidos.usuario("ADMIN"));

        for (String tamano : new String[] {"101", "100000", "0", "-3", "2147483647"}) {
            esUn400ConElMotivo(get("/api/v1/leadership/people?size=" + tamano, lider), "size debe estar entre 1 y 100");
        }
        esUn400ConElMotivo(get("/api/v1/leadership/people?page=-1", lider), "page no puede ser negativa");
        HttpResponse<String> enElLimite = get("/api/v1/leadership/people?size=100&page=0", lider);
        assertThat(enElLimite.statusCode()).as(enElLimite.body()).isEqualTo(200);
        assertThat(pedidos.json(enElLimite).get("size").asInt()).isEqualTo(100);
    }

    // spec 050 FR-034
    @Test
    @DisplayName("Auditoría de la consola de Soporte TI: size=101 o 100000 es 400 con el motivo; size=20 y size=100 pasan")
    void auditoriaDeLaConsola() throws Exception {
        String soporte = pedidos.sesionDe(pedidos.usuario("SOPORTE_TI"));
        Map<String, String> consola = Map.of("X-Consola-Token", pedidos.desbloquear(soporte));

        for (String tamano : new String[] {"101", "100000", "0", "-1"}) {
            esUn400ConElMotivo(pedidos.enviar("GET", "/api/v1/soporte-ti/auditoria?size=" + tamano, soporte, null, consola),
                    "size debe estar entre 1 y 100");
        }
        esUn400ConElMotivo(pedidos.enviar("GET", "/api/v1/soporte-ti/auditoria?page=-2", soporte, null, consola),
                "page no puede ser negativa");
        for (String tamano : new String[] {"20", "100"}) {
            HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/soporte-ti/auditoria?size=" + tamano, soporte,
                    null, consola);
            assertThat(respuesta.statusCode()).as(tamano + ": " + respuesta.body()).isEqualTo(200);
            JsonNode cuerpo = pedidos.json(respuesta);
            assertThat(cuerpo.get("contenido").size()).isLessThanOrEqualTo(Integer.parseInt(tamano));
        }
    }

    // spec 050 FR-034
    @Test
    @DisplayName("Cuotas de todos (la lista que ya tenía su tope): size=201 sigue siendo el mismo 400")
    void cuotasDeTodosSigueIgual() throws Exception {
        String admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));

        esUn400ConElMotivo(get("/api/v1/admin/pagos/cuotas?size=201", admin), "size debe estar entre 1 y 200");
    }

    // spec 050 FR-032
    @Test
    @DisplayName("la descarga del diagnóstico: ADMIN recibe el CSV con su BOM y su cabecera; un mentor, 403 en JSON y sin CSV")
    void descargaDelDiagnostico() throws Exception {
        String admin = pedidos.sesionDe(pedidos.usuario("ADMIN"));
        String mentor = pedidos.sesionDe(pedidos.usuario("MENTOR"));

        HttpResponse<byte[]> csv = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + puerto
                        + "/api/v1/admin/diagnostico/exportar.csv")).header("X-Auth-Token", admin).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("text/csv"));
        assertThat(csv.headers().firstValue("Content-Disposition")).hasValueSatisfying(t -> assertThat(t).contains("diagnostico.csv"));
        assertThat(csv.body()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String texto = new String(csv.body(), StandardCharsets.UTF_8).substring(1);
        assertThat(texto).startsWith("Sección,Clave,Pregunta,Ayuda,Tipo,Obligatoria,");
        assertThat(texto.lines().count()).as("la cabecera y al menos una pregunta del borrador").isGreaterThan(1);

        HttpResponse<String> rechazo = get("/api/v1/admin/diagnostico/exportar.csv", mentor);
        assertThat(rechazo.statusCode()).as(rechazo.body()).isEqualTo(403);
        assertThat(rechazo.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("application/json"));
        assertThat(rechazo.body()).doesNotContain("Sección,Clave");
    }
}

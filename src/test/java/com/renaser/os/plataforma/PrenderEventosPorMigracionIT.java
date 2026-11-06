package com.renaser.os.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.application.services.CopiaDeDespliegues;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V185: prende {@code eventos.meet}, {@code eventos.meet.transcripcion} y {@code eventos.whatsapp} para todos, por pedido
 * del dueño, como lo haría la consola de Soporte TI (fila de estado prendida, versión nueva, sin roles apagados, sin
 * despliegue y una fila de auditoría por cambio).
 *
 * <p>Flyway ya la aplicó al arrancar el contexto, pero las demás pruebas de la plataforma borran las filas de estado (para
 * probar el «nace apagada» del catálogo). Por eso cada prueba parte de una base sin esas filas y vuelve a correr el SQL de
 * la migración tal cual está en el classpath: también prueba que correrla dos veces no cambia nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PrenderEventosPorMigracionIT {

    private static final String MIGRACION = "db/migration/V185__prender_meet_transcripcion_y_whatsapp_de_eventos.sql";
    private static final List<String> CLAVES = List.of("eventos.meet", "eventos.meet.transcripcion", "eventos.whatsapp");
    private static final List<String> ROLES = List.of("SOPORTE_TI", "ALQUIMISTA", "ADMIN", "LIDER_MENTORES", "MENTOR",
            "GUIA_APRENDIZ", "APRENDIZ");
    private static final String SIN_ACTOR = "00000000-0000-0000-0000-000000000000";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SessionRepository<? extends Session> sesiones;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CopiaDeEstados copiaDeEstados;
    @Autowired
    private CopiaDeDespliegues copiaDeDespliegues;

    private PedidosDePrueba pedidos;
    private Timestamp desde;

    @BeforeEach
    void armar() {
        pedidos = new PedidosDePrueba(puerto, jdbc, sesiones, passwordEncoder);
        volverAlCatalogo();
        desde = Timestamp.from(Instant.now());
    }

    @AfterEach
    void limpiar() {
        volverAlCatalogo();
        pedidos.borrarCuentas();
    }

    @Test
    @DisplayName("Flyway la aplicó al arrancar")
    void flywayLaAplico() {
        String tabla = jdbc.queryForObject("SELECT table_schema || '.' || table_name FROM information_schema.tables "
                + "WHERE table_name = 'flyway_schema_history' LIMIT 1", String.class);
        assertThat(jdbc.queryForObject("SELECT success FROM " + tabla + " WHERE version = '185'", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("sin filas (nacen apagadas): después de la migración ningún rol las tiene en noDisponibles ni en apagadas, "
            + "y el ETag cambia")
    void prendeParaTodosLosRoles() throws Exception {
        String aprendiz = pedidos.sesionDe(pedidos.usuario("APRENDIZ"));
        HttpResponse<String> antes = mias(aprendiz);
        assertThat(textos(pedidos.json(antes).get("noDisponibles"))).as("el catálogo las hace nacer apagadas")
                .containsAll(CLAVES);
        String etagAntes = antes.headers().firstValue("ETag").orElseThrow();

        correrLaMigracion();

        for (String rol : ROLES) {
            JsonNode cuerpo = pedidos.json(mias(pedidos.sesionDe(pedidos.usuario(rol))));
            assertThat(textos(cuerpo.get("noDisponibles"))).as(rol).doesNotContainAnyElementsOf(CLAVES);
            List<String> apagadas = new ArrayList<>();
            cuerpo.get("apagadas").forEach(a -> apagadas.add(a.get("clave").asText()));
            assertThat(apagadas).as(rol).doesNotContainAnyElementsOf(CLAVES);
        }
        assertThat(mias(aprendiz).headers().firstValue("ETag").orElseThrow()).isNotEqualTo(etagAntes);
        for (String clave : CLAVES) {
            Map<String, Object> fila = jdbc.queryForMap("SELECT prendida_global, actualizado_por::text AS por "
                    + "FROM renaser.funcionalidades_estado WHERE clave = ?", clave);
            assertThat(fila.get("prendida_global")).isEqualTo(true);
            assertThat(fila.get("por")).isEqualTo(SIN_ACTOR);
        }
        List<Map<String, Object>> auditoria = auditoria();
        assertThat(auditoria).hasSize(3);
        assertThat(auditoria).allSatisfy(fila -> {
            assertThat(fila.get("tipo")).isEqualTo("MANUAL");
            assertThat(fila.get("motivo")).isEqualTo("Prendida por pedido del dueño (migración V185)");
            assertThat((String) fila.get("antes")).contains("\"prendidaGlobal\": false");
            assertThat((String) fila.get("despues")).contains("\"prendidaGlobal\": true").contains("\"apagadaPara\": []");
        });
    }

    @Test
    @DisplayName("una fila apagada para un rol y en despliegue al 25 % queda prendida para todos y sin despliegue; correrla "
            + "otra vez no cambia nada")
    void conFilasPreviasEsIdempotente() throws Exception {
        UUID soporte = pedidos.usuario("SOPORTE_TI");
        UUID probadora = pedidos.usuario("APRENDIZ");
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, aviso, version, actualizado_por, "
                + "actualizado_en) VALUES ('eventos.meet', true, 'Meet en prueba', nextval('renaser.funcionalidades_version_seq'), ?, now())",
                soporte);
        jdbc.update("INSERT INTO renaser.funcionalidades_estado_rol (clave, rol, prendida) VALUES ('eventos.meet', 'APRENDIZ', false)");
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('eventos.whatsapp', true, nextval('renaser.funcionalidades_version_seq'), ?, now())", soporte);
        jdbc.update("INSERT INTO renaser.funcionalidades_despliegue (clave, porcentaje, version, iniciado_en, actualizado_por, "
                + "actualizado_en) VALUES ('eventos.whatsapp', 25, nextval('renaser.funcionalidades_version_seq'), now(), ?, now())",
                soporte);
        jdbc.update("INSERT INTO renaser.funcionalidades_probadores (clave, usuario_id, sumado_por, sumado_en) "
                + "VALUES ('eventos.whatsapp', ?, ?, now())", probadora, soporte);

        correrLaMigracion();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_estado_rol WHERE clave IN "
                + "('eventos.meet', 'eventos.meet.transcripcion', 'eventos.whatsapp')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_despliegue WHERE clave = 'eventos.whatsapp'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT aviso FROM renaser.funcionalidades_estado WHERE clave = 'eventos.meet'",
                String.class)).as("el aviso se conserva").isEqualTo("Meet en prueba");
        JsonNode deOtra = pedidos.json(mias(pedidos.sesionDe(pedidos.usuario("APRENDIZ"))));
        assertThat(textos(deOtra.get("noDisponibles"))).doesNotContainAnyElementsOf(CLAVES);
        // eventos.meet (rol apagado), eventos.meet.transcripcion (sin fila) y el despliegue de eventos.whatsapp (su
        // interruptor ya estaba prendido para todos, así que solo cambia el despliegue).
        assertThat(auditoria()).extracting(fila -> fila.get("clave"))
                .containsExactlyInAnyOrder("eventos.meet", "eventos.meet.transcripcion", "eventos.whatsapp");
        assertThat(auditoria()).filteredOn(fila -> "eventos.whatsapp".equals(fila.get("clave"))).singleElement()
                .satisfies(fila -> {
                    assertThat((String) fila.get("antes")).contains("\"porcentaje\": 25").contains("\"probadores\": 1");
                    assertThat((String) fila.get("despues")).isEqualTo("{\"despliegue\": null}");
                });

        Map<String, Long> versiones = versiones();
        correrLaMigracion();
        assertThat(versiones()).as("otra corrida no sube versiones").isEqualTo(versiones);
        assertThat(auditoria()).as("ni audita de nuevo").hasSize(3);
    }

    private void correrLaMigracion() throws Exception {
        String sql = new ClassPathResource(MIGRACION).getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
        copiaDeDespliegues.recargar();
        copiaDeEstados.recargar();
    }

    private void volverAlCatalogo() {
        jdbc.update("DELETE FROM renaser.funcionalidades_despliegue WHERE clave IN "
                + "('eventos.meet', 'eventos.meet.transcripcion', 'eventos.whatsapp')");
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave IN "
                + "('eventos.meet', 'eventos.meet.transcripcion', 'eventos.whatsapp')");
        copiaDeDespliegues.recargar();
        copiaDeEstados.recargar();
    }

    private HttpResponse<String> mias(String sesion) throws Exception {
        HttpResponse<String> respuesta = pedidos.enviar("GET", "/api/v1/funcionalidades/mias", sesion, null);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return respuesta;
    }

    private List<Map<String, Object>> auditoria() {
        return jdbc.queryForList("SELECT tipo, clave, antes::text AS antes, despues::text AS despues, motivo "
                + "FROM renaser.cambios_auditados WHERE actor_id = ?::uuid AND dispositivo = 'Flyway V185' AND creado_en >= ?",
                SIN_ACTOR, desde);
    }

    private Map<String, Long> versiones() {
        Map<String, Long> versiones = new java.util.TreeMap<>();
        jdbc.query("SELECT clave, version FROM renaser.funcionalidades_estado WHERE clave IN "
                        + "('eventos.meet', 'eventos.meet.transcripcion', 'eventos.whatsapp')",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> versiones.put(rs.getString(1), rs.getLong(2)));
        return versiones;
    }

    private static List<String> textos(JsonNode arreglo) {
        List<String> textos = new ArrayList<>();
        arreglo.forEach(nodo -> textos.add(nodo.asText()));
        return textos;
    }
}

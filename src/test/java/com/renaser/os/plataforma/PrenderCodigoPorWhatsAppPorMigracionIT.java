package com.renaser.os.plataforma;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V186: prende {@code auth.codigo_whatsapp} (spec 043) para todos, por pedido del dueño, como lo haría la consola de Soporte TI. Igual que
 * {@link PrenderEventosPorMigracionIT}: cada prueba parte de la base sin la fila (nace apagada) y vuelve a correr el SQL tal cual está en el
 * classpath, lo que también prueba que correrlo dos veces no cambia nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PrenderCodigoPorWhatsAppPorMigracionIT {

    private static final String MIGRACION = "db/migration/V186__prender_codigo_por_whatsapp.sql";
    private static final String CLAVE = "auth.codigo_whatsapp";
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
        assertThat(jdbc.queryForObject("SELECT success FROM " + tabla + " WHERE version = '186'", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("apagada, GET /auth/whatsapp-code da 404; después de la migración deja de esconderse, con fila prendida y su auditoría")
    void laPrende() throws Exception {
        assertThat(disponibilidadSinSesion())
                .as("el catálogo la hace nacer apagada").isEqualTo(404);

        correrLaMigracion();

        assertThat(disponibilidadSinSesion()).isEqualTo(200);
        Map<String, Object> fila = jdbc.queryForMap("SELECT prendida_global, actualizado_por::text AS por "
                + "FROM renaser.funcionalidades_estado WHERE clave = ?", CLAVE);
        assertThat(fila.get("prendida_global")).isEqualTo(true);
        assertThat(fila.get("por")).isEqualTo(SIN_ACTOR);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_estado_rol WHERE clave = ?", Integer.class, CLAVE))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.cambios_auditados WHERE actor_id = ?::uuid "
                        + "AND dispositivo = 'Flyway V186' AND clave = ? AND motivo = 'Prendida por pedido del dueño (migración V186)' "
                        + "AND creado_en >= ?", Integer.class, SIN_ACTOR, CLAVE, desde)).isEqualTo(1);
    }

    @Test
    @DisplayName("correrla otra vez no cambia nada: ni versión nueva ni otra fila de auditoría")
    void esIdempotente() throws Exception {
        correrLaMigracion();
        Long version = jdbc.queryForObject("SELECT version FROM renaser.funcionalidades_estado WHERE clave = ?", Long.class, CLAVE);

        correrLaMigracion();

        assertThat(jdbc.queryForObject("SELECT version FROM renaser.funcionalidades_estado WHERE clave = ?", Long.class, CLAVE))
                .isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.cambios_auditados WHERE dispositivo = 'Flyway V186' "
                + "AND creado_en >= ?", Integer.class, desde)).isEqualTo(1);
    }

    @Test
    @DisplayName("si estaba apagada solo para un rol, queda prendida para todos")
    void quitaLosRolesApagados() throws Exception {
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES (?, true, nextval('renaser.funcionalidades_version_seq'), ?::uuid, now())", CLAVE, SIN_ACTOR);
        jdbc.update("INSERT INTO renaser.funcionalidades_estado_rol (clave, rol, prendida) VALUES (?, 'APRENDIZ', false)", CLAVE);

        correrLaMigracion();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_estado_rol WHERE clave = ?", Integer.class, CLAVE))
                .isZero();
    }

    /** El GET público, sin sesión, como lo hace la app antes de tener cuenta. */
    private int disponibilidadSinSesion() throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/auth/whatsapp-code")).GET().build();
        return HttpClient.newHttpClient().send(pedido, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private void correrLaMigracion() throws Exception {
        String sql = new ClassPathResource(MIGRACION).getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
        copiaDeDespliegues.recargar();
        copiaDeEstados.recargar();
    }

    private void volverAlCatalogo() {
        jdbc.update("DELETE FROM renaser.funcionalidades_despliegue WHERE clave = ?", CLAVE);
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = ?", CLAVE);
        copiaDeDespliegues.recargar();
        copiaDeEstados.recargar();
    }
}

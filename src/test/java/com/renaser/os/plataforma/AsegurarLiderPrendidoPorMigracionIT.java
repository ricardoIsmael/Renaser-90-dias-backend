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
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V187: asegura {@code lider} y {@code lider.cumplimiento} prendidas para todos (pedido del dueño). Nacen prendidas en el catálogo: sin fila no
 * hace nada; con una fila apagada (o roles apagados) la deja como la consola al prender «para todos».
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AsegurarLiderPrendidoPorMigracionIT {

    private static final String MIGRACION = "db/migration/V187__asegurar_lider_prendido.sql";
    private static final String SIN_ACTOR = "00000000-0000-0000-0000-000000000000";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CopiaDeEstados copiaDeEstados;
    @Autowired
    private CopiaDeDespliegues copiaDeDespliegues;

    private Timestamp desde;

    @BeforeEach
    void armar() {
        volverAlCatalogo();
        desde = Timestamp.from(Instant.now());
    }

    @AfterEach
    void limpiar() {
        volverAlCatalogo();
    }

    @Test
    @DisplayName("sin filas (prendidas por el catálogo) no crea nada ni audita")
    void sinFilaNoHaceNada() throws Exception {
        correrLaMigracion();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_estado WHERE clave IN ('lider', 'lider.cumplimiento')",
                Integer.class)).isZero();
        assertThat(auditadas()).isZero();
    }

    @Test
    @DisplayName("apagada desde la consola y con un rol apagado: queda prendida para todos, con una fila de auditoría; otra corrida no cambia nada")
    void laPrendeSiEstabaApagada() throws Exception {
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('lider', false, nextval('renaser.funcionalidades_version_seq'), ?::uuid, now())", SIN_ACTOR);
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('lider.cumplimiento', true, nextval('renaser.funcionalidades_version_seq'), ?::uuid, now())", SIN_ACTOR);
        jdbc.update("INSERT INTO renaser.funcionalidades_estado_rol (clave, rol, prendida) VALUES ('lider.cumplimiento', 'LIDER_MENTORES', false)");

        correrLaMigracion();

        assertThat(jdbc.queryForObject("SELECT bool_and(prendida_global) FROM renaser.funcionalidades_estado "
                + "WHERE clave IN ('lider', 'lider.cumplimiento')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.funcionalidades_estado_rol WHERE clave IN ('lider', 'lider.cumplimiento')",
                Integer.class)).isZero();
        assertThat(auditadas()).isEqualTo(2);

        correrLaMigracion();
        assertThat(auditadas()).isEqualTo(2);
    }

    private int auditadas() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.cambios_auditados WHERE dispositivo = 'Flyway V187' AND creado_en >= ?",
                Integer.class, desde);
    }

    private void correrLaMigracion() throws Exception {
        jdbc.execute(new ClassPathResource(MIGRACION).getContentAsString(StandardCharsets.UTF_8));
        copiaDeDespliegues.recargar();
        copiaDeEstados.recargar();
    }

    private void volverAlCatalogo() {
        jdbc.update("DELETE FROM renaser.funcionalidades_despliegue WHERE clave IN ('lider', 'lider.cumplimiento')");
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave IN ('lider', 'lider.cumplimiento')");
        copiaDeDespliegues.recargar();
        copiaDeEstados.recargar();
    }
}

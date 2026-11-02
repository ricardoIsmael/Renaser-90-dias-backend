package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V163 (auditoria de consultas del 2026-11-02, H-11): las cuatro claves foraneas de auditoria con {@code ON DELETE SET NULL} que no tenian indice ahora lo
 * tienen, parcial ({@code WHERE columna IS NOT NULL}). Sin el, borrar una cuenta recorria cada una de esas tablas entera. Los tres indices que podrian
 * sobrar NO se tocan (falta confirmar {@code idx_scan = 0} en produccion).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IndicesDeLaAuditoriaIT {

    private static final List<String> LOS_CUATRO = List.of("cuotas_de_pago_cambiada_por_idx", "ajustes_dia_programa_ajustado_por_idx",
            "estado_onboarding_ficha_asignada_por_idx", "participaciones_anteriores_trasladado_por_idx");
    private static final List<String> LOS_QUE_SE_QUEDAN = List.of("ranking_apr_lectura_idx", "analisis_80_20_formacion_idx", "celulas_formacion_idx");

    @Autowired
    private JdbcTemplate jdbc;

    private String definicion(String indice) {
        return jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname = 'renaser' AND indexname = ?", String.class, indice)
                .stream().findFirst().orElse(null);
    }

    @Test
    @DisplayName("V163: cada clave foranea de auditoria tiene su indice parcial, y solo guarda las filas que nombran a alguien")
    void losCuatroIndicesExistenYSonParciales() {
        for (String indice : LOS_CUATRO) {
            assertThat(definicion(indice)).as(indice).isNotNull().containsIgnoringCase("IS NOT NULL");
        }
    }

    @Test
    @DisplayName("V163 no borra los tres indices que parecen sobrar: falta confirmar idx_scan = 0 en produccion")
    void losTresPendientesSeQuedan() {
        for (String indice : LOS_QUE_SE_QUEDAN) {
            assertThat(definicion(indice)).as(indice).isNotNull();
        }
    }

    @Test
    @DisplayName("V163 es idempotente: volver a correrla no falla ni duplica nada")
    void esIdempotente() throws Exception {
        String sql = new ClassPathResource("db/migration/V163__indices_de_la_auditoria_de_consultas.sql").getContentAsString(StandardCharsets.UTF_8);

        jdbc.execute(sql);
        jdbc.execute(sql);

        String nombres = LOS_CUATRO.stream().map(n -> "'" + n + "'").collect(java.util.stream.Collectors.joining(", "));
        Integer cuantos = jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname = 'renaser' AND indexname IN (" + nombres + ")",
                Integer.class);
        assertThat(cuantos).isEqualTo(4);
    }
}

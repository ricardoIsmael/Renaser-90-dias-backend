package com.renaser.os.habits.infrastructure.adapter.out.persistence.habito;

import com.renaser.os.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V160 (spec 038, decision del dueno del 2026-11-02): «ULTIMA COMIDA DEL DIA» es de evidencia OPCIONAL en todos los entornos, para que la app muestre
 * el circulo y SER la marque con una propuesta en vez de pedirle una foto. El seed de V4 la dejaba OBLIGATORIA. La migracion solo toca ese id y es idempotente (se puede volver a correr sin mover nada).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UltimaComidaOpcionalIT {

    private static final UUID ULTIMA_COMIDA = UUID.fromString("3a8a82a0-787d-46bc-a69e-b29c110a37bc");

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Corre el SQL de la migracion en UNA conexion y le devuelve su search_path: la migracion empieza con {@code SET search_path TO renaser, public}
     * (regla de las migraciones) y un SET de sesion se queda en la conexion del pool, donde lo heredarian las pruebas siguientes.
     */
    private void correrLaMigracion(String sql) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (var st = con.createStatement()) {
                try {
                    st.execute(sql);
                } finally {
                    st.execute("RESET search_path");
                }
            }
            return null;
        });
    }

    private String exigencia(UUID habito) {
        return jdbc.queryForObject("SELECT exigencia_evidencia::text FROM renaser.habitos WHERE id = ?", String.class, habito);
    }

    // spec 038 FR-008
    @Test
    @DisplayName("V160: la ultima comida del dia queda de evidencia OPCIONAL")
    void laUltimaComidaEsOpcional() {
        assertThat(exigencia(ULTIMA_COMIDA)).isEqualTo("OPCIONAL");
    }

    // spec 038 FR-008
    @Test
    @DisplayName("V160 es idempotente: volver a correrla no cambia nada ni mueve la fecha de la ultima comida")
    void esIdempotente() throws Exception {
        String sql = new ClassPathResource("db/migration/V160__ultima_comida_es_de_evidencia_opcional.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var antes = jdbc.queryForObject("SELECT actualizado_en FROM renaser.habitos WHERE id = ?", java.sql.Timestamp.class, ULTIMA_COMIDA);

        correrLaMigracion(sql);
        correrLaMigracion(sql);

        assertThat(exigencia(ULTIMA_COMIDA)).isEqualTo("OPCIONAL");
        assertThat(jdbc.queryForObject("SELECT actualizado_en FROM renaser.habitos WHERE id = ?", java.sql.Timestamp.class, ULTIMA_COMIDA))
                .isEqualTo(antes);
    }
}

package com.renaser.os.mentoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.renaser.os.mentoring.application.ports.in.ConsultarEvaluacionPropiaUseCase.EvaluacionPropia;
import com.renaser.os.rocks.api.ObjetivosDelAprendizResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que la spec 057 promete sin tocar la base ni a las apps ya instaladas: ninguna tabla nueva (SC-001) y respuestas solo aditivas (SC-003).
 */
class Spec057SinTablasNuevasTest {

    private static final Path MIGRACIONES = Path.of("src/main/resources/db/migration");

    @Test
    @DisplayName("spec 057 SC-001: desde la V200 la única migración de la spec es un valor de enum, sin CREATE TABLE")
    void unaSolaMigracionSinTablas() throws IOException { // spec 057 SC-001
        try (Stream<Path> archivos = Files.list(MIGRACIONES)) {
            List<Path> desdeLaV200 = archivos.filter(a -> a.getFileName().toString().matches("V(\\d+)__.*\\.sql"))
                    .filter(a -> Integer.parseInt(a.getFileName().toString().replaceAll("^V(\\d+)__.*$", "$1")) >= 200).toList();

            assertThat(desdeLaV200).as("solo la V200 de esta spec").hasSize(1);
            String sql = Files.readString(desdeLaV200.getFirst());
            assertThat(desdeLaV200.getFirst().getFileName().toString()).startsWith("V200__");
            assertThat(sql.toUpperCase()).doesNotContain("CREATE TABLE").doesNotContain("ALTER TABLE");
            assertThat(sql).contains("ALTER TYPE tipo_notificacion ADD VALUE IF NOT EXISTS 'ROJO_DEL_DIA'");
        }
    }

    @Test
    @DisplayName("spec 057 SC-003: la evaluación del mentor conserva todos sus campos de siempre y solo suma porcentajeMesAnterior, quienBaja y acciones")
    void laEvaluacionSoloSuma() throws Exception { // spec 057 SC-003, FR-010
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        EvaluacionPropia evaluacion = new EvaluacionPropia("2026-10", "America/Lima", new BigDecimal("75"), 3, 4, 2, 0, 1, 0, "CALCULADA", "v1",
                Instant.parse("2026-10-30T12:00:00Z"), List.of());

        JsonNode cuerpo = json.readTree(json.writeValueAsString(evaluacion));

        assertThat(cuerpo.fieldNames()).toIterable().contains("mes", "zona", "porcentaje", "entregadas", "esperadas", "alumnosEvaluados",
                "alumnosExcluidos", "tardiasFueraDeVentana", "verificadas", "estado", "versionFormula", "corteEn", "tramos")
                .contains("porcentajeMesAnterior", "quienBaja", "acciones");
        assertThat(cuerpo.get("quienBaja").isArray()).isTrue();
        assertThat(cuerpo.get("acciones").isArray()).isTrue();
        assertThat(cuerpo.get("porcentajeMesAnterior").isNull()).as("sin mes anterior calculado va null").isTrue();
    }

    @Test
    @DisplayName("spec 057 SC-003 y FR-001: la respuesta de objetivos conserva noventaDias, mes y semana, y suma duracionDias y fase")
    void losObjetivosSoloSuman() { // spec 057 SC-003, FR-001
        assertThat(ObjetivosDelAprendizResponse.class.getRecordComponents()).extracting("name")
                .containsExactly("noventaDias", "mes", "semana", "duracionDias", "fase");
    }
}

package com.renaser.os.onboarding.domain.model.diagnostico;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El CSV del diagnóstico (spec 050, tramo F; SC-008), byte a byte contra un archivo dorado capturado con el código de antes
 * de pasar a streaming: mismas columnas, mismo orden y mismo escape (comas, comillas, saltos de línea en la ayuda, códigos de
 * opción, escala, condición, destino y formaciones).
 */
class ExportacionDelDiagnosticoTest {

    static final Path DORADO = Path.of("src/test/resources/exportaciones/diagnostico-dorado.csv");

    // spec 050 FR-032, SC-008
    @Test
    @DisplayName("el CSV de la ficha de siempre es idéntico, byte a byte, al de antes del streaming")
    void elCsvDeSiempre() throws IOException {
        String actual = PlantillaDeImportacion.exportar(FichasParaLaExportacion.deSiempre());

        assertThat(actual.getBytes(StandardCharsets.UTF_8)).isEqualTo(Files.readAllBytes(DORADO));
    }

    // spec 050 FR-032, SC-008
    @Test
    @DisplayName("por filas da lo mismo: la cabecera primero y después una fila por pregunta, y juntas son el CSV de antes")
    void porFilasEsElMismoCsv() throws IOException {
        FichaDelDiagnostico ficha = FichasParaLaExportacion.deSiempre();
        List<String> filas = new ArrayList<>();

        PlantillaDeImportacion.exportarPorFilas(ficha, filas::add);

        assertThat(filas).hasSize(1 + 6);
        assertThat(filas.get(0)).startsWith("Sección,Clave,Pregunta,").endsWith("\r\n");
        assertThat(filas).allSatisfy(fila -> assertThat(fila).endsWith("\r\n"));
        assertThat(String.join("", filas).getBytes(StandardCharsets.UTF_8)).isEqualTo(Files.readAllBytes(DORADO));
    }
}

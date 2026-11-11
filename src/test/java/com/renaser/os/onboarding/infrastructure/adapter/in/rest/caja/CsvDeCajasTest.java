package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El CSV de la Caja (spec 050, tramo F; SC-008), byte a byte contra un archivo dorado capturado con el código de antes
 * de pasar a streaming: mismo contenido, mismas columnas, mismo orden, mismo escape (BOM, «;», fórmulas con apóstrofo,
 * comillas y saltos de línea). Si esta prueba falla, la descarga cambió para el Admin.
 */
class CsvDeCajasTest {

    static final Path DORADO = Path.of("src/test/resources/exportaciones/caja-dorado.csv");
    static final Path DORADO_DE_MUCHAS = Path.of("src/test/resources/exportaciones/caja-dorado-120-filas.csv");

    // spec 050 FR-032, SC-008
    @Test
    @DisplayName("el CSV de ocho filas de todo tipo es idéntico, byte a byte, al de antes del streaming")
    void elCsvDeSiempre() throws IOException {
        byte[] actual = CsvDeCajas.escribir(FilasDeCajaParaLasPruebas.deSiempre());

        assertThat(actual).isEqualTo(Files.readAllBytes(DORADO));
    }

    // spec 050 FR-032, SC-008
    @Test
    @DisplayName("el CSV de 120 filas (más de un lote de los que usa el padrón) es idéntico al de antes")
    void elCsvDeMuchasFilas() throws IOException {
        byte[] actual = CsvDeCajas.escribir(FilasDeCajaParaLasPruebas.varias(120));

        assertThat(actual).isEqualTo(Files.readAllBytes(DORADO_DE_MUCHAS));
    }
}

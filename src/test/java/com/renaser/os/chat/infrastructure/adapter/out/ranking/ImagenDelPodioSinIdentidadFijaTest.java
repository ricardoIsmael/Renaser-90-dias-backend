package com.renaser.os.chat.infrastructure.adapter.out.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Identidad por formación (regla 7): la imagen del podio lleva el nombre de la formación de su comunidad, nunca un nombre ni un plazo fijos. */
class ImagenDelPodioSinIdentidadFijaTest {

    @Test
    @DisplayName("la imagen del podio no escribe «90 DÍAS» ni un nombre fijo: usa el nombre de la formación que le pasan")
    void sinIdentidadFija() throws IOException {
        Path fuente = Path.of("src/main/java/com/renaser/os/chat/infrastructure/adapter/out/ranking/PodioJava2dAdapter.java");
        String codigo = Files.readString(fuente);

        assertThat(codigo).as("el pie y el rótulo salen del nombre de la formación (nombreDeLaFormacion), no de un texto fijo")
                .doesNotContainIgnoringCase("90 DÍAS").doesNotContainIgnoringCase("90 DIAS")
                .doesNotContain("\"FORMACIÓN RENASER").doesNotContain("\"RENASER ·");
    }

    @Test
    @DisplayName("la marca es el nombre de la formación en mayúsculas; sin nombre cae a «RENASER» sin plazo")
    void marca() {
        assertThat(PodioJava2dAdapter.marcaDe("Escuela Renaser")).isEqualTo("ESCUELA RENASER");
        assertThat(PodioJava2dAdapter.marcaDe("  Diplomado Renaser ")).isEqualTo("DIPLOMADO RENASER");
        assertThat(PodioJava2dAdapter.marcaDe(null)).isEqualTo("RENASER");
        assertThat(PodioJava2dAdapter.marcaDe(" ")).isEqualTo("RENASER");
    }
}

package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Una prueba que sube el nivel de un logger (para capturar lo que escribe) tiene que devolverlo al terminar.
 * Si no, el resto de la suite corre en TRACE y el registro del CI crece hasta cortarse (le pasó al CD de #106:
 * {@code TranscripcionesDeMeetServiceTest} dejaba la raíz en ALL).
 */
class NivelDelLogSeRestauraTest {

    private static final Pattern CAMBIO_DE_NIVEL = Pattern.compile("\\.setLevel\\(");

    @Test
    @DisplayName("Toda prueba que cambia el nivel de un logger lo vuelve a poner como estaba")
    void elNivelSeRestaura() throws IOException {
        List<String> sinRestaurar;
        try (Stream<Path> archivos = Files.walk(Path.of("src/test/java"))) {
            sinRestaurar = archivos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("NivelDelLogSeRestauraTest.java"))
                    .filter(NivelDelLogSeRestauraTest::cambiaElNivelSinDevolverlo)
                    .map(Path::toString)
                    .toList();
        }
        assertThat(sinRestaurar).as("pruebas que suben el nivel de un logger y no lo devuelven").isEmpty();
    }

    private static boolean cambiaElNivelSinDevolverlo(Path archivo) {
        String codigo;
        try {
            codigo = Files.readString(archivo);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Matcher m = CAMBIO_DE_NIVEL.matcher(codigo);
        int cambios = 0;
        while (m.find()) {
            cambios++;
        }
        // Subirlo es un setLevel; devolverlo es otro (normalmente en @AfterEach).
        return cambios == 1;
    }
}

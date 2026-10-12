package com.renaser.os.habits.application.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 001 (D-264, T019): el catálogo activo se lee SOLO a través de {@link CatalogoAplicable}. Un servicio que
 * vuelva a llamar a {@code catalogoActivo()} le mostraría a una persona hábitos que no son de su rol.
 */
class CatalogoSoloPorElFiltroTest {

    private static final Path MODULO = Path.of("src/main/java/com/renaser/os/habits");
    private static final List<String> PERMITIDOS = List.of(
            "application/services/CatalogoAplicable.java",
            "application/ports/out/habito/LoadHabitoPort.java",
            "infrastructure/adapter/out/persistence/habito/HabitoPersistenceAdapter.java");

    @Test
    @DisplayName("nadie fuera del filtro llama a catalogoActivo()")
    void nadieLeeElCatalogoSinFiltrar() throws IOException {
        try (Stream<Path> archivos = Files.walk(MODULO)) {
            List<String> infractores = archivos.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> PERMITIDOS.stream().noneMatch(permitido -> p.toString().endsWith(permitido)))
                    .filter(CatalogoSoloPorElFiltroTest::llamaAlCatalogoActivo)
                    .map(Path::toString).toList();
            assertThat(infractores).isEmpty();
        }
    }

    private static boolean llamaAlCatalogoActivo(Path archivo) {
        try {
            return Files.readString(archivo).contains("catalogoActivo()");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.renaser.os.rag.domain.model.memoria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-167: lo que devuelve el modelo no se guarda tal cual; esta es la segunda capa. */
class CompactacionTest {

    private static final Instant ANTES = Instant.parse("2026-09-20T15:00:00Z");
    private static final MemoriaDeRenasia SIN_NADA = MemoriaDeRenasia.vacia();

    @Test
    @DisplayName("lo emocional y la salud se descartan, aunque el modelo los devuelva")
    void sinLoSensible() {
        var compactacion = new Compactacion("Hablaron de su trabajo.", Map.of(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA,
                List.of("Trabaja de noche", "Tiene ansiedad los lunes", "Toma medicamentos para dormir")));

        assertThat(compactacion.textosQueQuedan(SIN_NADA))
                .containsExactly(Map.entry(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, List.of("Trabaja de noche")));
    }

    @Test
    @DisplayName("los nombres de habitos del programa no cuentan como salud: Pastilla Renacer y Audioterapia pasan")
    void habitosDelProgramaNoSonSalud() {
        var compactacion = new Compactacion("", Map.of(CategoriaDeRecuerdo.METAS_Y_LO_QUE_FUNCIONA,
                List.of("Le cuesta la Pastilla Renacer de la manana", "La Audioterapia le sirve de noche")));

        assertThat(compactacion.textosQueQuedan(SIN_NADA).get(CategoriaDeRecuerdo.METAS_Y_LO_QUE_FUNCIONA)).hasSize(2);
    }

    @Test
    @DisplayName("sin ids, sin repetidos, sin textos largos y a lo sumo seis por categoria")
    void limites() {
        String largo = "x".repeat(Recuerdo.LARGO_MAXIMO + 1);
        var compactacion = new Compactacion("", Map.of(CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO, List.of(
                "Prefiere respuestas cortas", "Prefiere respuestas cortas", largo,
                "Su habito 899a2151-e98c-4b61-a46c-b55134240d17", "a", "b", "c", "d", "e", "f", "g")));

        assertThat(compactacion.textosQueQuedan(SIN_NADA).get(CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO))
                .containsExactly("Prefiere respuestas cortas", "a", "b", "c", "d", "e");
    }

    @Test
    @DisplayName("una categoria que el modelo no devolvio conserva lo que habia; una que devolvio vacia, se vacia")
    void categoriaQueFaltaSeConserva() {
        var actual = new MemoriaDeRenasia(List.of(
                new Recuerdo(UUID.randomUUID(), CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche", ANTES),
                new Recuerdo(UUID.randomUUID(), CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO, "Respuestas cortas", ANTES)),
                Optional.empty(), ANTES);
        var compactacion = new Compactacion("", Map.of(CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO, List.of()));

        assertThat(compactacion.textosQueQuedan(actual))
                .containsExactly(Map.entry(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, List.of("Trabaja de noche")));
    }

    @Test
    @DisplayName("el resumen se tapa de ids y se descarta si trae algo sensible")
    void resumen() {
        assertThat(new Compactacion("Hablo de su crisis de ansiedad.", Map.of()).resumenSaneado()).isEmpty();
        assertThat(new Compactacion("  Quiere caminar mas.  ", null).resumenSaneado()).isEqualTo("Quiere caminar mas.");
    }
}

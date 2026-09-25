package com.renaser.os.rag.domain.model.memoria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoriaDeRenasiaTest {

    private static final Instant ANTES = Instant.parse("2026-09-20T15:00:00Z");

    @Test
    @DisplayName("sin memoria, el prompt lo dice para que el modelo no invente una")
    void vacia() {
        assertThat(MemoriaDeRenasia.vacia().paraElModelo()).contains("Todavia no sabes nada en particular");
        assertThat(MemoriaDeRenasia.vacia().compactadoHasta()).isEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("agrupa por categoria, en su orden, y agrega el resumen al final")
    void conRecuerdosYResumen() {
        var memoria = new MemoriaDeRenasia(List.of(
                new Recuerdo(UUID.randomUUID(), CategoriaDeRecuerdo.PREFERENCIAS_DE_TRATO, "Prefiere respuestas cortas", ANTES),
                new Recuerdo(UUID.randomUUID(), CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche", ANTES)),
                Optional.of("Armaron su rutina de la manana."), ANTES);

        assertThat(memoria.paraElModelo()).isEqualTo("""
                Contexto de vida:
                - Trabaja de noche
                Como prefiere que la acompanes:
                - Prefiere respuestas cortas
                Lo que venian conversando antes: Armaron su rutina de la manana.""");
    }

    @Test
    @DisplayName("un resumen en blanco es lo mismo que no tener resumen; uno demasiado largo no entra")
    void resumen() {
        assertThat(new MemoriaDeRenasia(List.of(), Optional.of("   "), ANTES).estaVacia()).isTrue();
        assertThatThrownBy(() -> new MemoriaDeRenasia(List.of(),
                Optional.of("x".repeat(MemoriaDeRenasia.LARGO_MAXIMO_DEL_RESUMEN + 1)), ANTES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encuentra el recuerdo que ya estaba con el mismo texto y la misma categoria")
    void recuerdoIgual() {
        var trabaja = new Recuerdo(UUID.randomUUID(), CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche", ANTES);
        var memoria = new MemoriaDeRenasia(List.of(trabaja), Optional.empty(), ANTES);

        assertThat(memoria.recuerdo(CategoriaDeRecuerdo.CONTEXTO_DE_VIDA, "Trabaja de noche")).contains(trabaja);
        assertThat(memoria.recuerdo(CategoriaDeRecuerdo.METAS_Y_LO_QUE_FUNCIONA, "Trabaja de noche")).isEmpty();
    }
}

package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisión del dueño (2026-10-31): el objetivo grande se nombra según la duración del programa de la persona. En la Escuela (90 días) el prompt no cambia;
 * en el Diplomado de 1 año le dice al modelo cómo llamarlo.
 */
class NombreDelObjetivoGrandeEnElPromptTest {

    @Test
    @DisplayName("con 90 días no se agrega nada: es el nombre de siempre")
    void conNoventaDiasNoCambiaNada() {
        assertThat(GoogleGenAiRenasiaChatAdapter.nombreDelObjetivoGrande(90)).isEmpty();
    }

    @Test
    @DisplayName("con 365 días le dice al modelo que lo llame «objetivo de 1 año»")
    void conUnAnioLoNombraAsi() {
        assertThat(GoogleGenAiRenasiaChatAdapter.nombreDelObjetivoGrande(365))
                .contains("objetivo de 1 año")
                .endsWith("\n");
    }
}

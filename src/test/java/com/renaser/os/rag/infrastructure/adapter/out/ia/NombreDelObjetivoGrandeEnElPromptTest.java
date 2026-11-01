package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.shared.domain.Formacion;
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
        assertThat(GoogleGenAiRenasiaChatAdapter.nombreDelObjetivoGrande(
                new SituacionDelAprendiz(5, 1).conDuracion(90).plazoDelObjetivo())).isEmpty();
    }

    @Test
    @DisplayName("con 365 días le dice al modelo que lo llame «objetivo de 1 año»")
    void conUnAnioLoNombraAsi() {
        assertThat(GoogleGenAiRenasiaChatAdapter.nombreDelObjetivoGrande(
                new SituacionDelAprendiz(5, 1).conDuracion(365).plazoDelObjetivo()))
                .contains("objetivo de 1 año")
                .endsWith("\n");
    }

    @Test
    @DisplayName("el Diplomado de 3 meses se llama «objetivo de 3 meses»: la misma duración que la Escuela, otro nombre")
    void elDiplomadoDeTresMeses() {
        SituacionDelAprendiz diplomado = new SituacionDelAprendiz(5, 1).conDuracion(90).conFormacion(Formacion.DIPLOMADO);
        SituacionDelAprendiz escuela = new SituacionDelAprendiz(5, 1).conDuracion(90).conFormacion(Formacion.ESCUELA);
        SituacionDelAprendiz certificacion = new SituacionDelAprendiz(5, 1).conDuracion(90).conFormacion(Formacion.CERTIFICACION);

        assertThat(GoogleGenAiRenasiaChatAdapter.nombreDelObjetivoGrande(diplomado.plazoDelObjetivo())).contains("objetivo de 3 meses");
        assertThat(GoogleGenAiRenasiaChatAdapter.nombreDelObjetivoGrande(escuela.plazoDelObjetivo())).isEmpty();
        assertThat(GoogleGenAiRenasiaChatAdapter.nombreDelObjetivoGrande(certificacion.plazoDelObjetivo())).isEmpty();
        assertThat(new SituacionDelAprendiz(5, 1).conDuracion(365).conFormacion(Formacion.DIPLOMADO).plazoDelObjetivo()).isEqualTo("1 año");
    }
}

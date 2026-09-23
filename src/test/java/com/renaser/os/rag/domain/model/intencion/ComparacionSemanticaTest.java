package com.renaser.os.rag.domain.model.intencion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ComparacionSemanticaTest {

    private static final List<Float> AGUA = List.of(1f, 0f, 0f);
    private static final List<Float> LEER = List.of(0f, 1f, 0f);

    @Test
    @DisplayName("elige la etiqueta mas parecida y el margen contra la segunda")
    void eligeLaMasParecida() {
        List<Float> mensaje = List.of(0.9f, 0.1f, 0f);

        Candidato candidato = ComparacionSemantica.mejorCandidato(mensaje,
                List.of(new Referencia("r-agua", AGUA), new Referencia("r-leer", LEER))).orElseThrow();

        assertThat(candidato.etiqueta()).isEqualTo("r-agua");
        assertThat(candidato.margen()).isCloseTo(candidato.similitud() - ComparacionSemantica.coseno(mensaje, LEER),
                within(1e-9));
    }

    @Test
    @DisplayName("una etiqueta con varias referencias vale la mas parecida, no el promedio")
    void variasReferenciasPorEtiqueta() {
        Candidato candidato = ComparacionSemantica.mejorCandidato(LEER, List.of(
                new Referencia("leer", List.of(0.5f, 0.5f, 0.7f)),
                new Referencia("leer", LEER),
                new Referencia("agua", AGUA))).orElseThrow();

        assertThat(candidato.etiqueta()).isEqualTo("leer");
        assertThat(candidato.similitud()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("con una sola etiqueta el margen es su propia similitud")
    void unaSolaEtiqueta() {
        Candidato candidato = ComparacionSemantica.mejorCandidato(AGUA, List.of(new Referencia("agua", AGUA)))
                .orElseThrow();

        assertThat(candidato.margen()).isEqualTo(candidato.similitud());
    }

    @Test
    @DisplayName("sin referencias no hay candidato")
    void sinReferencias() {
        assertThat(ComparacionSemantica.mejorCandidato(AGUA, List.of())).isEmpty();
    }

    @Test
    @DisplayName("un vector de ceros (adaptador noop) no se parece a nada: coseno 0, sin dividir por cero")
    void vectorNulo() {
        assertThat(ComparacionSemantica.coseno(List.of(0f, 0f, 0f), AGUA)).isZero();
    }

    @Test
    @DisplayName("dimensiones distintas es un error de configuracion, no un cero silencioso")
    void dimensionesDistintas() {
        assertThatThrownBy(() -> ComparacionSemantica.coseno(List.of(1f, 0f), AGUA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

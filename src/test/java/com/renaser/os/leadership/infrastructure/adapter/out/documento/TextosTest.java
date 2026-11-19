package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Las palabras sueltas del documento (spec 056, FR-035 y FR-036): el porcentaje de una evaluación y el singular o plural de un conteo. */
class TextosTest {

    @Test
    @DisplayName("spec 056 FR-035: una evaluación se dice como porcentaje entero redondeado, igual que en la app: 13,23 es «13 %» y 82,5 es «83 %»")
    void laEvaluacionSeDiceEntera() {
        assertThat(Textos.evaluacion(new BigDecimal("13.2300"))).isEqualTo("13 %");
        assertThat(Textos.evaluacion(new BigDecimal("82.5"))).isEqualTo("83 %");
        assertThat(Textos.evaluacion(new BigDecimal("60"))).isEqualTo("60 %");
        assertThat(Textos.evaluacion(new BigDecimal("100.0000"))).isEqualTo("100 %");
    }

    @Test
    @DisplayName("spec 056 FR-035: el 0.1323 que sale del servidor ya es un porcentaje (0,13 %), no una fracción: se dice «0 %» y nunca «13 %»")
    void unaEvaluacionMinimaNoSeMultiplica() {
        assertThat(Textos.evaluacion(new BigDecimal("0.1323"))).isEqualTo("0 %");
        assertThat(Textos.evaluacion(BigDecimal.ZERO)).isEqualTo("0 %");
    }

    @Test
    @DisplayName("spec 056 FR-036: un conteo concuerda con su palabra: 1 consulta, 2 consultas, 0 consultas")
    void laCantidadConcuerda() {
        assertThat(Textos.cantidad(1, "consulta", "consultas")).isEqualTo("1 consulta");
        assertThat(Textos.cantidad(2, "consulta", "consultas")).isEqualTo("2 consultas");
        assertThat(Textos.cantidad(0, "día", "días")).isEqualTo("0 días");
    }
}

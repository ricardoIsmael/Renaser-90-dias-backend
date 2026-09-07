package com.renaser.os.rocks.domain.model.rocamaestra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La parte medible del objetivo de 90 dias: que combinaciones valen y como sale el porcentaje. */
class MetaCuantitativaTest {

    private static MetaCuantitativa meta(String objetivo, String avance) {
        return new MetaCuantitativa(new BigDecimal(objetivo), new BigDecimal(avance), "USD");
    }

    @Test
    @DisplayName("el porcentaje es el avance sobre la meta, truncado a entero")
    void calculaElPorcentaje() {
        assertThat(meta("30000", "19500").porcentaje()).isEqualTo(65);
        assertThat(meta("30000", "0").porcentaje()).isZero();
        assertThat(meta("30000", "30000").porcentaje()).isEqualTo(100);
        assertThat(meta("3", "1").porcentaje()).as("33,33% trunca a 33, no redondea a 34").isEqualTo(33);
    }

    @Test
    @DisplayName("superar la meta no pasa de 100: quien consume esto es una barra de progreso")
    void acotaElPorcentajeA100() {
        assertThat(meta("30000", "42000").porcentaje()).isEqualTo(100);
        assertThat(meta("30000", "42000").avance())
                .as("pero el valor real no se pierde")
                .isEqualByComparingTo("42000");
    }

    @Test
    @DisplayName("una meta de cero o negativa no tiene sentido: no habria contra que medir")
    void rechazaMetaNoPositiva() {
        assertThatThrownBy(() -> meta("0", "10")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> meta("-5", "10")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("un avance negativo tampoco")
    void rechazaAvanceNegativo() {
        assertThatThrownBy(() -> meta("100", "-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sin unidad el numero no se puede ni escribir en pantalla")
    void exigeUnidad() {
        assertThatThrownBy(() -> new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, "x".repeat(21)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la unidad se guarda sin espacios de sobra")
    void recortaLaUnidad() {
        assertThat(new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, "  kg  ").unidad()).isEqualTo("kg");
    }

    @Test
    @DisplayName("una meta nueva arranca en cero")
    void metaNuevaArrancaEnCero() {
        MetaCuantitativa nueva = MetaCuantitativa.nueva(new BigDecimal("30000"), "USD");

        assertThat(nueva.avance()).isEqualByComparingTo("0");
        assertThat(nueva.porcentaje()).isZero();
    }

    @Test
    @DisplayName("registrar avance conserva meta y unidad")
    void conAvanceConservaLoDemas() {
        MetaCuantitativa actualizada = meta("30000", "0").conAvance(new BigDecimal("19500"));

        assertThat(actualizada.objetivo()).isEqualByComparingTo("30000");
        assertThat(actualizada.unidad()).isEqualTo("USD");
        assertThat(actualizada.porcentaje()).isEqualTo(65);
    }
}

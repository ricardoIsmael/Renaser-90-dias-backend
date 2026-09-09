package com.renaser.os.rocks.domain.model.rocamaestra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La parte medible del objetivo de 90 dias: que combinaciones valen y como sale el porcentaje. */
class MetaCuantitativaTest {

    private static MetaCuantitativa meta(String objetivo, String avance) {
        return new MetaCuantitativa(new BigDecimal(objetivo), new BigDecimal(avance), "USD", null);
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
        assertThatThrownBy(() -> new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, "x".repeat(21), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la unidad se guarda sin espacios de sobra")
    void recortaLaUnidad() {
        assertThat(new MetaCuantitativa(BigDecimal.TEN, BigDecimal.ONE, "  kg  ", null).unidad()).isEqualTo("kg");
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

    // ==========================================================================================
    // E-166 · El porcentaje con punto de partida
    // ==========================================================================================

    private static MetaCuantitativa desde(String base, String avance, String meta) {
        return new MetaCuantitativa(new BigDecimal(meta), new BigDecimal(avance), "kg", new BigDecimal(base));
    }

    @Test
    @DisplayName("E-166: una meta que BAJA arranca en 0 %, no en 100 %")
    void metaDescendenteArrancaEnCero() {
        // El caso exacto del bug: "pesare 75 kg partiendo de 82". Contra el codigo viejo esto daba
        // 82 * 100 / 75 = 109, acotado a 100, y el Plan mostraba "100 % CUMPLIDO" el primer dia.
        assertThat(desde("82", "82", "75").porcentaje()).isZero();
    }

    @Test
    @DisplayName("E-166: una meta que baja avanza a medida que el numero baja")
    void metaDescendenteAvanzaAlBajar() {
        assertThat(desde("82", "78.5", "75").porcentaje()).isEqualTo(50);
        assertThat(desde("82", "75", "75").porcentaje()).isEqualTo(100);
        // Pasarse para el lado bueno sigue siendo 100, no mas.
        assertThat(desde("82", "70", "75").porcentaje()).isEqualTo(100);
    }

    @Test
    @DisplayName("E-166: engordar por encima del punto de partida es 0 %, nunca negativo")
    void alejarseDeLaMetaEsCero() {
        assertThat(desde("82", "85", "75").porcentaje()).isZero();
    }

    @Test
    @DisplayName("E-166: una meta que SUBE se mide desde su punto de partida, no desde cero")
    void metaAscendenteSeMideDesdeLaBase() {
        MetaCuantitativa facturacion = new MetaCuantitativa(new BigDecimal("15000"),
                new BigDecimal("5000"), "S/", new BigDecimal("5000"));
        // Sin linea base esto daba 33 % el primer dia por los 5000 que ya facturaba. Con ella, el
        // avance del programa es 0: todavia no gano un sol mas de los que ya ganaba.
        assertThat(facturacion.porcentaje()).isZero();
        assertThat(facturacion.conAvance(new BigDecimal("10000")).porcentaje()).isEqualTo(50);
        assertThat(facturacion.conAvance(new BigDecimal("15000")).porcentaje()).isEqualTo(100);
        assertThat(facturacion.conAvance(new BigDecimal("3000")).porcentaje()).isZero();
    }

    @Test
    @DisplayName("E-166: sin linea base se conserva la formula vieja, para las filas anteriores a V43")
    void sinLineaBaseSeConservaLaFormulaVieja() {
        assertThat(new MetaCuantitativa(new BigDecimal("30000"), new BigDecimal("15000"), "USD", null)
                .porcentaje()).isEqualTo(50);
    }

    @Test
    @DisplayName("E-166: la linea base no puede ser igual a la meta, no habria avance que medir")
    void lineaBaseIgualALaMetaSeRechaza() {
        assertThatThrownBy(() -> desde("75", "75", "75"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinta del punto de partida");
    }

    @Test
    @DisplayName("E-166: `conAvance` conserva el punto de partida")
    void conAvanceConservaLaLineaBase() {
        assertThat(desde("82", "82", "75").conAvance(new BigDecimal("80")).lineaBase())
                .isEqualByComparingTo("82");
    }
}

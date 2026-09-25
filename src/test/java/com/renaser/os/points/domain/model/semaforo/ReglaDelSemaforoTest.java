package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglaDelSemaforoTest {

    @ParameterizedTest(name = "{0} de {1} = {2} %")
    @CsvSource({"7,9,78", "2,3,67", "1,3,33", "1,8,13", "0,5,0", "5,5,100", "1,2,50"})
    void elPorcentajeDelDiaRedondeaAEnteroMitadHaciaArriba(int cumplidos, int programados, int esperado) {
        assertThat(ReglaDelSemaforo.porcentajeDelDia(cumplidos, programados)).isEqualTo(esperado);
    }

    @Test
    void unDiaSinNadaProgramadoNoTienePorcentaje() {
        assertThatThrownBy(() -> ReglaDelSemaforo.porcentajeDelDia(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cumplidosNoPuedenSuperarLoProgramado() {
        assertThatThrownBy(() -> ReglaDelSemaforo.porcentajeDelDia(4, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinDiasConDatosNoHayPromedioNiCeroNiCien() {
        assertThat(ReglaDelSemaforo.promedio(List.of())).isEmpty();
    }

    /** Doble redondeo (coherence.ts:97,130): cada dia a entero, el promedio a un decimal. */
    @Test
    void elPromedioEsDeLosPorcentajesDiariosConUnDecimal() {
        assertThat(ReglaDelSemaforo.promedio(List.of(100, 67))).contains(new BigDecimal("83.5"));
        assertThat(ReglaDelSemaforo.promedio(List.of(100, 100, 67))).contains(new BigDecimal("89.0"));
        assertThat(ReglaDelSemaforo.promedio(List.of(67, 17))).contains(new BigDecimal("42.0"));
        assertThat(ReglaDelSemaforo.promedio(List.of(80, 79, 79))).contains(new BigDecimal("79.3"));
    }

    @ParameterizedTest(name = "{0} % es {1}")
    @CsvSource({"100.0,VERDE", "80.0,VERDE", "79.9,AMARILLO", "60.0,AMARILLO", "59.9,ROJO", "0.0,ROJO"})
    void losUmbralesSonOchentaYSesenta(String porcentaje, ColorSemaforo esperado) {
        assertThat(ReglaDelSemaforo.colorDe(new BigDecimal(porcentaje))).isEqualTo(esperado);
    }

    @Test
    void sinPorcentajeEsSinDatosNuncaVerde() {
        assertThat(ReglaDelSemaforo.colorDe(null)).isEqualTo(ColorSemaforo.SIN_DATOS);
    }

    @Test
    void cadaColorViajaConSuPalabra() {
        assertThat(ColorSemaforo.VERDE.etiqueta()).isEqualTo("Al día");
        assertThat(ColorSemaforo.AMARILLO.etiqueta()).isEqualTo("Requiere atención");
        assertThat(ColorSemaforo.ROJO.etiqueta()).isEqualTo("Con problemas");
        assertThat(ColorSemaforo.SIN_DATOS.etiqueta()).isEqualTo("Sin datos");
    }
}

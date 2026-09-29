package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los cortes que pidió el dueño para la tarjeta diaria (D-223) son los del semáforo (D-168/D-181), en un solo
 * lugar: verde ≥ 80, amarillo 60–79,9, rojo &lt; 60.
 */
class CortesDeLaTarjetaTest {

    @ParameterizedTest(name = "{0} % → {1}")
    @CsvSource({"59.9, ROJO", "60, AMARILLO", "60.0, AMARILLO", "79.9, AMARILLO", "80, VERDE", "100, VERDE", "0, ROJO"})
    @DisplayName("D-223: cortes 59,9 / 60 / 79,9 / 80")
    void cortes(String porcentaje, ColorSemaforo color) {
        assertThat(ReglaDelSemaforo.colorDe(new BigDecimal(porcentaje))).isEqualTo(color);
    }

    @ParameterizedTest(name = "día al {0} % → {1}")
    @CsvSource({"59, ROJO", "60, AMARILLO", "79, AMARILLO", "80, VERDE"})
    @DisplayName("D-223: el color de un día (entero) usa los mismos cortes")
    void cortesDelDia(int porcentaje, ColorSemaforo color) {
        assertThat(ReglaDelSemaforo.colorDelDia(porcentaje)).isEqualTo(color);
    }
}

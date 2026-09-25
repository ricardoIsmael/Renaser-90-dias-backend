package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Conteo por color, medición de un aprendiz y resumen de un grupo: reglas puras, sin Spring. */
class ResumenDelGrupoTest {

    private static MedicionDelAprendiz medido(String porcentaje, ColorSemaforo color) {
        return new MedicionDelAprendiz(new BigDecimal(porcentaje), color, 7, List.of());
    }

    @Test
    @DisplayName("el color del promedio usa los umbrales de una persona: 80 verde, 79,9 amarillo, 59,9 rojo")
    void colorDelPromedio() {
        assertThat(ResumenDelGrupo.de(List.of(medido("80.0", ColorSemaforo.VERDE))).colorDelPromedio())
                .isEqualTo(ColorSemaforo.VERDE);
        assertThat(ResumenDelGrupo.de(List.of(medido("79.9", ColorSemaforo.AMARILLO))).colorDelPromedio())
                .isEqualTo(ColorSemaforo.AMARILLO);
        assertThat(ResumenDelGrupo.de(List.of(medido("60.0", ColorSemaforo.AMARILLO))).colorDelPromedio())
                .isEqualTo(ColorSemaforo.AMARILLO);
        assertThat(ResumenDelGrupo.de(List.of(medido("59.9", ColorSemaforo.ROJO))).colorDelPromedio())
                .isEqualTo(ColorSemaforo.ROJO);
    }

    @Test
    @DisplayName("un grupo sin nadie con datos no tiene promedio: su color es sin datos, nunca verde")
    void sinPromedioSinColor() {
        assertThat(ResumenDelGrupo.de(List.of()).colorDelPromedio()).isEqualTo(ColorSemaforo.SIN_DATOS);
    }

    @Test
    @DisplayName("el promedio redondea mitad hacia arriba a 1 decimal: 80,0 y 72,5 dan 76,3 (no 76,2)")
    void promedioMitadHaciaArriba() {
        ResumenDelGrupo resumen = ResumenDelGrupo.de(List.of(
                medido("80.0", ColorSemaforo.VERDE), medido("72.5", ColorSemaforo.AMARILLO)));

        assertThat(resumen.promedio()).isEqualByComparingTo("76.3");
        assertThat(resumen.promedio().scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("los sin datos no entran al promedio (ni como cero), pero si al conteo")
    void sinDatosFueraDelPromedio() {
        ResumenDelGrupo resumen = ResumenDelGrupo.de(List.of(
                medido("90.0", ColorSemaforo.VERDE), medido("50.0", ColorSemaforo.ROJO),
                MedicionDelAprendiz.SIN_MEDICION));

        assertThat(resumen.promedio()).isEqualByComparingTo("70.0");
        assertThat(resumen.conteo()).isEqualTo(new ConteoPorColor(1, 0, 1, 1));
        assertThat(resumen.conteo().total()).isEqualTo(3);
    }

    @Test
    @DisplayName("sin nadie con datos el promedio es null, no cero; un grupo vacio cuenta cero en todo")
    void sinNadieConDatos() {
        assertThat(ResumenDelGrupo.de(List.of(MedicionDelAprendiz.SIN_MEDICION)).promedio()).isNull();
        assertThat(ResumenDelGrupo.de(List.of())).isEqualTo(new ResumenDelGrupo(ConteoPorColor.NINGUNO, null));
    }

    @Test
    @DisplayName("un aprendiz sin ventana es SIN_MEDICION: sin datos, sin porcentaje, cero dias, sin dias")
    void sinVentanaEsSinMedicion() {
        MedicionDelAprendiz medicion = MedicionDelAprendiz.de(null);

        assertThat(medicion.color()).isEqualTo(ColorSemaforo.SIN_DATOS);
        assertThat(medicion.porcentaje()).isNull();
        assertThat(medicion.diasConDatos()).isZero();
        assertThat(medicion.dias()).isEmpty();
    }

    @Test
    @DisplayName("una medicion no puede ser verde sin porcentaje ni tener porcentaje sin color")
    void medicionCoherente() {
        assertThatThrownBy(() -> new MedicionDelAprendiz(null, ColorSemaforo.VERDE, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MedicionDelAprendiz(BigDecimal.TEN, ColorSemaforo.SIN_DATOS, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los totales se suman color por color y el total se deriva")
    void conteoSeSuma() {
        ConteoPorColor suma = new ConteoPorColor(5, 2, 1, 0).mas(new ConteoPorColor(1, 0, 3, 2));

        assertThat(suma).isEqualTo(new ConteoPorColor(6, 2, 4, 2));
        assertThat(suma.total()).isEqualTo(14);
    }
}

package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El encabezado de una vista de grupo: fechas y si ya cerró. Semana sábado→viernes (D-168). */
class PeriodoDelSemaforoTest {

    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);
    private static final LocalDate SABADO_26 = LocalDate.of(2026, 9, 26);

    private static VentanaDelSemaforo ventana(LocalDate desde, boolean cerrada) {
        return new VentanaDelSemaforo(desde, desde.plusDays(6), null, ColorSemaforo.SIN_DATOS, 0, cerrada, List.of());
    }

    @Test
    @DisplayName("la ventana vigente son los 7 dias que terminan ayer; un viernes todavia no cerro")
    void vigenteUnViernes() {
        PeriodoDelSemaforo periodo = PeriodoDelSemaforo.esperado(VIERNES_25, null);

        assertThat(periodo).isEqualTo(new PeriodoDelSemaforo(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 24), false));
    }

    @Test
    @DisplayName("el sabado la vigente coincide con la semana que acaba de cerrar")
    void vigenteUnSabado() {
        PeriodoDelSemaforo periodo = PeriodoDelSemaforo.esperado(SABADO_26, null);

        assertThat(periodo).isEqualTo(new PeriodoDelSemaforo(LocalDate.of(2026, 9, 19), VIERNES_25, true));
    }

    @Test
    @DisplayName("una semana pedida va de sabado a viernes; cerrada si ya paso, abierta si es la que corre")
    void semanaPedida() {
        assertThat(PeriodoDelSemaforo.esperado(VIERNES_25, LocalDate.of(2026, 9, 18)))
                .isEqualTo(new PeriodoDelSemaforo(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18), true));
        assertThat(PeriodoDelSemaforo.esperado(VIERNES_25, VIERNES_25))
                .isEqualTo(new PeriodoDelSemaforo(LocalDate.of(2026, 9, 19), VIERNES_25, false));
    }

    @Test
    @DisplayName("una semana que no termina en viernes no existe")
    void semanaQueNoTerminaEnViernes() {
        assertThatThrownBy(() -> PeriodoDelSemaforo.esperado(VIERNES_25, LocalDate.of(2026, 9, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("con ventanas que coinciden, sus fechas; cerrada solo si TODAS lo estan")
    void ventanasQueCoinciden() {
        PeriodoDelSemaforo esperado = PeriodoDelSemaforo.esperado(VIERNES_25, null);
        LocalDate sabado12 = LocalDate.of(2026, 9, 12);

        assertThat(PeriodoDelSemaforo.de(List.of(ventana(sabado12, true), ventana(sabado12, true)), esperado))
                .isEqualTo(new PeriodoDelSemaforo(sabado12, LocalDate.of(2026, 9, 18), true));
        assertThat(PeriodoDelSemaforo.de(List.of(ventana(sabado12, true), ventana(sabado12, false)), esperado).cerrada())
                .isFalse();
    }

    @Test
    @DisplayName("con ventanas de zonas distintas que no coinciden, las fechas del esperado; sin ventanas, el esperado")
    void ventanasQueNoCoinciden() {
        PeriodoDelSemaforo esperado = PeriodoDelSemaforo.esperado(VIERNES_25, null);

        PeriodoDelSemaforo mezclado = PeriodoDelSemaforo.de(
                List.of(ventana(LocalDate.of(2026, 9, 18), false), ventana(LocalDate.of(2026, 9, 19), false)), esperado);

        assertThat(mezclado.desde()).isEqualTo(esperado.desde());
        assertThat(mezclado.hasta()).isEqualTo(esperado.hasta());
        assertThat(PeriodoDelSemaforo.de(List.of(), esperado)).isEqualTo(esperado);
    }
}

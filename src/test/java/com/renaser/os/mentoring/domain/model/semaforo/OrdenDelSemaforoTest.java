package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrdenDelSemaforoTest {

    @Test
    @DisplayName("primero lo rojo, despues lo amarillo, despues los sin datos y al final lo verde")
    void prioridadDeColores() {
        List<ColorSemaforo> colores = new ArrayList<>(List.of(
                ColorSemaforo.VERDE, ColorSemaforo.SIN_DATOS, ColorSemaforo.ROJO, ColorSemaforo.AMARILLO));

        colores.sort(OrdenDelSemaforo.porPrioridad());

        assertThat(colores).containsExactly(
                ColorSemaforo.ROJO, ColorSemaforo.AMARILLO, ColorSemaforo.SIN_DATOS, ColorSemaforo.VERDE);
    }

    @Test
    @DisplayName("por nombre es alfabetico castellano: Ángela antes que Beto, y los sin nombre al final")
    void alfabeticoCastellano() {
        List<String> nombres = new ArrayList<>(Arrays.asList("Zoila Díaz", null, "Beto Paz", "Ángela Ruiz"));

        nombres.sort(OrdenDelSemaforo.alfabetico());

        // Con String.compareTo, "Ángela" quedaba al final: la Á esta despues de la Z en Unicode.
        assertThat(nombres).containsExactly("Ángela Ruiz", "Beto Paz", "Zoila Díaz", null);
    }
}

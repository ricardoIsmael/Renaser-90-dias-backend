package com.renaser.os.onboarding.domain.model.terminos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Qué impide guardar y qué impide publicar un texto (spec 027, FR-007). */
class ContenidoDeTerminosTest {

    private static ContenidoDeTerminos con(List<String> clausulas) {
        return new ContenidoDeTerminos("Términos y condiciones", "Intro", clausulas, "Acepto");
    }

    @Test
    @DisplayName("un texto completo se puede publicar y se guarda sin avisos")
    void completo() {
        var c = con(List.of("Una", "Dos"));
        assertThat(c.loQueFaltaParaPublicar()).isEmpty();
        c.exigirQueSePuedaGuardar();
    }

    @Test
    @DisplayName("sin cláusulas, con una vacía, sin título o sin casilla no se publica, y dice cuál")
    void loQueFalta() {
        assertThat(con(List.of()).loQueFaltaParaPublicar()).containsExactly("Agrega al menos una cláusula.");
        assertThat(con(List.of("Una", "   ", "Tres")).loQueFaltaParaPublicar()).containsExactly("La cláusula 2 está vacía.");
        assertThat(new ContenidoDeTerminos(" ", "", List.of("Una"), "Acepto").loQueFaltaParaPublicar()).containsExactly("Falta el título.");
        assertThat(new ContenidoDeTerminos("T", "", List.of("Una"), " ").loQueFaltaParaPublicar()).containsExactly("Falta el texto de la casilla de aceptación.");
    }

    @Test
    @DisplayName("un borrador con una cláusula vacía SÍ se guarda (es trabajo en curso), uno que pasa de los límites no")
    void guardar() {
        con(List.of("Una", "")).exigirQueSePuedaGuardar();
        assertThatThrownBy(() -> con(List.of("x".repeat(2001))).exigirQueSePuedaGuardar()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cláusula 1");
        assertThatThrownBy(() -> con(Collections.nCopies(101, "x")).exigirQueSePuedaGuardar()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> con(List.of()).exigirQueSePuedaGuardar()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContenidoDeTerminos("t".repeat(121), "", List.of("Una"), "Acepto").exigirQueSePuedaGuardar()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los espacios de los bordes no cuentan: «Una » y «Una» dicen lo mismo, y publicar sin cambios se detecta")
    void dicenLoMismo() {
        assertThat(con(List.of("Una ", " Dos")).dicenLoMismo(con(List.of("Una", "Dos")))).isTrue();
        assertThat(con(List.of("Una", "Dos")).dicenLoMismo(con(List.of("Dos", "Una")))).as("el orden importa").isFalse();
    }

    @Test
    @DisplayName("los saltos de línea de una cláusula se respetan")
    void saltosDeLinea() {
        assertThat(con(List.of("Primera línea\nSegunda línea")).clausulas().get(0)).contains("\n");
    }
}

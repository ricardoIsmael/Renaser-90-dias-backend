package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.terminos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiteralDeArregloTest {

    @Test
    @DisplayName("cada cláusula va entrecomillada: las comas, las llaves y los acentos no cambian el arreglo")
    void entrecomilla() {
        assertThat(LiteralDeArreglo.deTextos(List.of("Uno, dos", "{tres}", "Cláusula §4 — «cuatro»"))).isEqualTo("{\"Uno, dos\",\"{tres}\",\"Cláusula §4 — «cuatro»\"}");
    }

    @Test
    @DisplayName("la comilla y la barra se escapan")
    void escapa() {
        assertThat(LiteralDeArreglo.deTextos(List.of("Dijo \"sí\"", "ruta\\carpeta"))).isEqualTo("{\"Dijo \\\"sí\\\"\",\"ruta\\\\carpeta\"}");
    }

    @Test
    @DisplayName("sin elementos es el arreglo vacío")
    void vacio() {
        assertThat(LiteralDeArreglo.deTextos(List.of())).isEqualTo("{}");
    }
}

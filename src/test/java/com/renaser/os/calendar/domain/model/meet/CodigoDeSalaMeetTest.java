package com.renaser.os.calendar.domain.model.meet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodigoDeSalaMeetTest {

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: el código de la sala sale del enlace de Meet")
    void elCodigoSaleDelEnlace() {
        assertThat(CodigoDeSalaMeet.deEnlace("https://meet.google.com/abc-defg-hij")).contains("abc-defg-hij");
        assertThat(CodigoDeSalaMeet.deEnlace("  https://meet.google.com/abc-defg-hij  ")).contains("abc-defg-hij");
        assertThat(CodigoDeSalaMeet.deEnlace("https://MEET.google.com/abc-defg-hij")).contains("abc-defg-hij");
    }

    // spec 040 FR-023
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "   ", "https://zoom.us/j/123", "https://meet.google.com/", "https://meet.google.com/lookup/abc",
            "https://meet.google.com/ABC-DEFG-HIJ", "https://meet.google.com/abcdefghij", "javascript:alert(1)", "no es un enlace"})
    @DisplayName("FR-023: un enlace que no es de una sala de Meet no tiene código")
    void unEnlaceQueNoEsDeMeetNoTieneCodigo(String enlace) {
        assertThat(CodigoDeSalaMeet.deEnlace(enlace)).isEmpty();
    }

    // spec 040 FR-023
    @Test
    void nuloNoTieneCodigo() {
        assertThat(CodigoDeSalaMeet.deEnlace(null)).isEmpty();
    }
}

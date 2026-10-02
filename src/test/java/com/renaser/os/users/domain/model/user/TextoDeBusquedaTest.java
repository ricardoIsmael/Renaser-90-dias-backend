package com.renaser.os.users.domain.model.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoDeBusquedaTest {

    @Test
    @DisplayName("D-249: tildes, diéresis, ñ y mayúsculas se normalizan igual que en la base")
    void normalizaComoLaBase() {
        assertThat(TextoDeBusqueda.de("  MARÍA   José Peña Güemes ").orElseThrow().normalizado())
                .isEqualTo("maria jose pena guemes");
        assertThat(TextoDeBusqueda.de("ÁÉÍÓÚ àèìòù ç").orElseThrow().normalizado()).isEqualTo("aeiou aeiou c");
    }

    @Test
    @DisplayName("D-249: nada escrito no busca nada")
    void vacioNoBusca() {
        assertThat(TextoDeBusqueda.de(null)).isEmpty();
        assertThat(TextoDeBusqueda.de("   ")).isEmpty();
    }

    @Test
    @DisplayName("D-249: un texto enorme se acota")
    void acotaElLargo() {
        assertThat(TextoDeBusqueda.de("a".repeat(500)).orElseThrow().normalizado())
                .hasSize(TextoDeBusqueda.LARGO_MAXIMO);
    }
}

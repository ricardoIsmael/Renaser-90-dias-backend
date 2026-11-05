package com.renaser.os.notifications.domain.model.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumeroDeWhatsAppTest {

    // spec 041 FR-013
    @Test
    @DisplayName("lo que guarda la ficha («+51 987654321») se normaliza a formato internacional sin espacios")
    void normalizaLaFicha() {
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+51 987654321")).get().extracting(NumeroDeWhatsApp::valor).isEqualTo("+51987654321");
        assertThat(NumeroDeWhatsApp.desdeLaFicha("  +51 987 654 321  ")).get().extracting(NumeroDeWhatsApp::valor).isEqualTo("+51987654321");
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+1 (415) 555-2671")).get().extracting(NumeroDeWhatsApp::valor).isEqualTo("+14155552671");
    }

    // spec 041 FR-013, SC-004
    @Test
    @DisplayName("sin «+», muy corto, muy largo, con letras o vacio no es un numero: no sale nada y queda «Sin WhatsApp»")
    void loQueNoEsUnNumero() {
        assertThat(NumeroDeWhatsApp.desdeLaFicha(null)).isEmpty();
        assertThat(NumeroDeWhatsApp.desdeLaFicha("")).isEmpty();
        assertThat(NumeroDeWhatsApp.desdeLaFicha("987654321")).isEmpty();
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+51 98765")).isEmpty();
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+51 9876543219876543")).isEmpty();
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+51 98765432a")).isEmpty();
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+0 987654321")).isEmpty();
    }

    // spec 041 FR-028
    @Test
    @DisplayName("se enmascara como «+51 9•• ••• •21» y nunca muestra el numero completo, tampoco en toString")
    void seEnmascara() {
        var numero = NumeroDeWhatsApp.desdeLaFicha("+51 987654321").orElseThrow();

        assertThat(numero.enmascarado()).isEqualTo("+51 9•• ••• •21");
        assertThat(numero.toString()).isEqualTo("+51 9•• ••• •21").doesNotContain("87654");
    }

    // spec 041 FR-028
    @Test
    @DisplayName("otros largos de numero tambien dejan ver solo el codigo de pais, un digito y los dos ultimos")
    void otrosLargos() {
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+14155552671").orElseThrow().enmascarado()).isEqualTo("+14 1•• ••• •71");
        assertThat(NumeroDeWhatsApp.desdeLaFicha("+5491155551234").orElseThrow().enmascarado()).doesNotContain("1155551");
    }
}

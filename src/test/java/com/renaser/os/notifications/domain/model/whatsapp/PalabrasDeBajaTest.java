package com.renaser.os.notifications.domain.model.whatsapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PalabrasDeBajaTest {

    // spec 041 FR-027, SC-011
    @Test
    @DisplayName("«BAJA» entero vale, sin contar mayusculas, tildes, signos ni espacios de mas")
    void unaBajaEntera() {
        for (String texto : new String[] {"BAJA", "baja", "  Baja!  ", "¡BAJA!", "stop", "STOP.", "darme de baja", "no más", "NO MAS", "dar  de  baja"}) {
            assertThat(PalabrasDeBaja.esBaja(texto)).as(texto).isTrue();
        }
    }

    // spec 041 FR-027
    @Test
    @DisplayName("una frase que solo contiene la palabra es una conversacion, no una baja")
    void unaConversacionNoEsUnaBaja() {
        for (String texto : new String[] {"¿hay baja de precio?", "no quiero dar de baja mi tarjeta", "gracias", "ok", "", "   ", "bajar", "bajas"}) {
            assertThat(PalabrasDeBaja.esBaja(texto)).as(texto).isFalse();
        }
        assertThat(PalabrasDeBaja.esBaja(null)).isFalse();
    }

    // spec 041 FR-027
    @Test
    @DisplayName("la carga del boton lleva el id de la entrega y solo una carga nuestra se reconoce")
    void laCargaDelBoton() {
        UUID id = UUID.randomUUID();

        assertThat(PalabrasDeBaja.cargaDelBoton(id)).isEqualTo("BAJA:" + id);
        assertThat(PalabrasDeBaja.entregaDeLaCarga("BAJA:" + id)).contains(id);
        assertThat(PalabrasDeBaja.entregaDeLaCarga("  baja:" + id.toString().toUpperCase() + " ")).contains(id);
        assertThat(PalabrasDeBaja.entregaDeLaCarga("HECHO:" + id)).as("el boton de otra plantilla de la cuenta no es nuestro").isEmpty();
        assertThat(PalabrasDeBaja.entregaDeLaCarga("BAJA:no-es-un-uuid")).isEmpty();
        assertThat(PalabrasDeBaja.entregaDeLaCarga(null)).isEmpty();
    }
}

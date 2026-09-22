package com.renaser.os.rag.domain.model.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MensajeDeApoyoTest {

    @Test
    @DisplayName("mientras el texto del MINSA no este confirmado, no se muestra nada")
    void sinConfigurarNoDevuelveTexto() {
        assertThat(MensajeDeApoyo.sinConfigurar().estaConfigurado()).isFalse();
        assertThat(MensajeDeApoyo.sinConfigurar().paraMostrar()).isEmpty();
    }

    /**
     * Una propiedad escrita como {@code mensaje: "   "} en el YAML, o un
     * {@code RENASIA_MENSAJE_APOYO=" "} exportado sin querer, no puede convertirse en un mensaje en
     * blanco mostrado a alguien que esta mal.
     */
    @Test
    void unaPropiedadEnBlancoCuentaComoNoConfigurada() {
        assertThat(new MensajeDeApoyo("   ").paraMostrar()).isEmpty();
        assertThat(new MensajeDeApoyo(null).paraMostrar()).isEmpty();
    }

    /** Sin ningun numero ni horario de ejemplo, ni siquiera en una prueba: el del MINSA no esta
     * confirmado y un valor inventado que ande dando vueltas en el repo termina copiado. */
    @Test
    void devuelveElTextoConfiguradoSinEspaciosDeMas() {
        assertThat(new MensajeDeApoyo("  Texto de prueba, no el del MINSA  ").paraMostrar())
                .contains("Texto de prueba, no el del MINSA");
    }
}

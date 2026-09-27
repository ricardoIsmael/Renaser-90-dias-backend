package com.renaser.os.chat.domain.model.conversacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Qué foto representa a cada integrante de un grupo o de un soporte (D-206). */
class FotoDeIntegrantesTest {

    @Test
    @DisplayName("TARJETA: la tarjeta siempre, haya subido foto o no")
    void conTarjetaSiempre() {
        assertThat(FotoDeIntegrantes.TARJETA.llevaTarjeta(true)).isTrue();
        assertThat(FotoDeIntegrantes.TARJETA.llevaTarjeta(false)).isTrue();
    }

    @Test
    @DisplayName("FOTO_SUBIDA: la tarjeta solo para quien no subió foto")
    void conFotoSubidaSoloSinFoto() {
        assertThat(FotoDeIntegrantes.FOTO_SUBIDA.llevaTarjeta(true)).isFalse();
        assertThat(FotoDeIntegrantes.FOTO_SUBIDA.llevaTarjeta(false)).isTrue();
    }

    @Test
    @DisplayName("se lee sin importar mayúsculas ni espacios; lo que no es un modo no se adivina")
    void seLeeDeLaConfiguracion() {
        assertThat(FotoDeIntegrantes.de(" foto_subida ")).contains(FotoDeIntegrantes.FOTO_SUBIDA);
        assertThat(FotoDeIntegrantes.de("Tarjeta")).contains(FotoDeIntegrantes.TARJETA);
        assertThat(FotoDeIntegrantes.de("tarjetas")).isEmpty();
        assertThat(FotoDeIntegrantes.de("")).isEmpty();
        assertThat(FotoDeIntegrantes.de(null)).isEmpty();
    }
}

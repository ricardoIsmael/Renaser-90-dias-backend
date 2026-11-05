package com.renaser.os.notifications.domain.model.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HuecoDePlantillaTest {

    // spec 041 FR-017
    @Test
    @DisplayName("un hueco vacio dice «—»: Meta rechaza uno vacio")
    void vacio() {
        assertThat(HuecoDePlantilla.limpiar(null)).isEqualTo("—");
        assertThat(HuecoDePlantilla.limpiar("  \n\t ")).isEqualTo("—");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("se recorta a 400 caracteres con «…»")
    void largo() {
        String limpio = HuecoDePlantilla.limpiar("a".repeat(500));

        assertThat(limpio).hasSize(HuecoDePlantilla.LARGO_MAXIMO).endsWith("…");
    }

    // spec 041 FR-017
    @Test
    @DisplayName("junta saltos de linea, tabuladores y espacios repetidos en un solo espacio")
    void espacios() {
        assertThat(HuecoDePlantilla.limpiar("uno\n\n  dos\t\ttres      cuatro")).isEqualTo("uno dos tres cuatro");
    }
}

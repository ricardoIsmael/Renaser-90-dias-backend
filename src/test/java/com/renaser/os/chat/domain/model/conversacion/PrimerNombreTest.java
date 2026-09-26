package com.renaser.os.chat.domain.model.conversacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrimerNombreTest {

    @Test
    @DisplayName("solo la primera palabra, con la inicial en mayúscula (D-173)")
    void primeraPalabraConMayuscula() {
        assertThat(PrimerNombre.de("María José Ñahui Quispe")).isEqualTo("María");
        assertThat(PrimerNombre.de("  ñahui  quispe ")).isEqualTo("Ñahui");
        assertThat(PrimerNombre.de("ana")).isEqualTo("Ana");
    }

    @Test
    @DisplayName("sin nombre legible devuelve vacío, nunca null")
    void sinNombreVacio() {
        assertThat(PrimerNombre.de(null)).isEmpty();
        assertThat(PrimerNombre.de("   ")).isEmpty();
    }
}

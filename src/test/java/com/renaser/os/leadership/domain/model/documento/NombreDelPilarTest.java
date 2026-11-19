package com.renaser.os.leadership.domain.model.documento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NombreDelPilarTest {

    @Test
    @DisplayName("spec 056 FR-035: los cuatro pilares con el nombre de la ficha del mentor: CONSCIENCIA es «Emociones»")
    void losCuatroPilares() {
        assertThat(NombreDelPilar.de("CUERPO")).isEqualTo("Cuerpo");
        assertThat(NombreDelPilar.de("MENTE")).isEqualTo("Mente");
        assertThat(NombreDelPilar.de("ESPIRITU")).isEqualTo("Espíritu");
        assertThat(NombreDelPilar.de("CONSCIENCIA")).isEqualTo("Emociones");
    }

    @Test
    @DisplayName("spec 056 FR-035: un pilar de otra formación sale en tipo oración y un hábito sin pilar, en «Otros»")
    void otrosPilares() {
        assertThat(NombreDelPilar.de("RELACIONES_FAMILIARES")).isEqualTo("Relaciones familiares");
        assertThat(NombreDelPilar.de("trabajo")).isEqualTo("Trabajo");
        assertThat(NombreDelPilar.de(null)).isEqualTo("Otros");
        assertThat(NombreDelPilar.de("  ")).isEqualTo("Otros");
    }
}

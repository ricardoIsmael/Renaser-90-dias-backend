package com.renaser.os.rag.domain.model.espejosombra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistribucionTemporalTest {

    @Test
    void aceptaPorcentajesQueSumanCien() {
        DistribucionTemporal distribucion = new DistribucionTemporal(30, 50, 20);

        assertThat(distribucion.pctPasado()).isEqualTo(30);
        assertThat(distribucion.pctPresente()).isEqualTo(50);
        assertThat(distribucion.pctFuturo()).isEqualTo(20);
    }

    @Test
    void rechazaSumaMenorA100() {
        assertThatThrownBy(() -> new DistribucionTemporal(10, 20, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sumar 100");
    }

    @Test
    void rechazaSumaMayorA100() {
        assertThatThrownBy(() -> new DistribucionTemporal(40, 40, 40))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sumar 100");
    }

    @Test
    void rechazaPorcentajeNegativo() {
        assertThatThrownBy(() -> new DistribucionTemporal(-10, 60, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaPorcentajeMayorA100() {
        assertThatThrownBy(() -> new DistribucionTemporal(150, -30, -20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceptaCasosLimite() {
        assertThat(new DistribucionTemporal(100, 0, 0).pctPasado()).isEqualTo(100);
        assertThat(new DistribucionTemporal(0, 0, 100).pctFuturo()).isEqualTo(100);
    }
}

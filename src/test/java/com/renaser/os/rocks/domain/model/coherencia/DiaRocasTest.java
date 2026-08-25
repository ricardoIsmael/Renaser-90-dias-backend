package com.renaser.os.rocks.domain.model.coherencia;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaRocasTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);

    @Test
    void puntajeDelDiaRedondeaAEnteroPrimerRedondeo() {
        assertThat(new DiaRocas(FECHA, 3, 1).puntajeDelDia()).isEqualTo(33); // 33.33... -> 33
        assertThat(new DiaRocas(FECHA, 3, 2).puntajeDelDia()).isEqualTo(67); // 66.66... -> 67
        assertThat(new DiaRocas(FECHA, 2, 1).puntajeDelDia()).isEqualTo(50);
        assertThat(new DiaRocas(FECHA, 3, 0).puntajeDelDia()).isEqualTo(0);
        assertThat(new DiaRocas(FECHA, 3, 3).puntajeDelDia()).isEqualTo(100);
    }

    @Test
    void fechaObligatoria() {
        assertThatThrownBy(() -> new DiaRocas(null, 3, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalDebeSerMayorACero() {
        assertThatThrownBy(() -> new DiaRocas(FECHA, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiaRocas(FECHA, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completadasNoPuedeSerNegativoNiSuperarElTotal() {
        assertThatThrownBy(() -> new DiaRocas(FECHA, 3, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiaRocas(FECHA, 3, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

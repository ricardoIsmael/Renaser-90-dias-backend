package com.renaser.os.rocks.domain.model.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoRitmoRocasTest {

    @Test
    @DisplayName("5 o mas dias completados de 7 -> OK")
    void cincoOMasEsOk() {
        assertThat(EstadoRitmoRocas.calcular(5)).isEqualTo(EstadoRitmoRocas.OK);
        assertThat(EstadoRitmoRocas.calcular(7)).isEqualTo(EstadoRitmoRocas.OK);
    }

    @Test
    @DisplayName("3 o 4 dias completados -> LENTO")
    void tresOCuatroEsLento() {
        assertThat(EstadoRitmoRocas.calcular(3)).isEqualTo(EstadoRitmoRocas.LENTO);
        assertThat(EstadoRitmoRocas.calcular(4)).isEqualTo(EstadoRitmoRocas.LENTO);
    }

    @Test
    @DisplayName("menos de 3 dias completados -> CRITICO")
    void menosDeTresEsCritico() {
        assertThat(EstadoRitmoRocas.calcular(0)).isEqualTo(EstadoRitmoRocas.CRITICO);
        assertThat(EstadoRitmoRocas.calcular(2)).isEqualTo(EstadoRitmoRocas.CRITICO);
    }
}

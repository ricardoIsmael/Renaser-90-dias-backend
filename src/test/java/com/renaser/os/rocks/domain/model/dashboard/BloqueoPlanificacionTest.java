package com.renaser.os.rocks.domain.model.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BloqueoPlanificacionTest {

    @Test
    @DisplayName("antes del dia 31 de programa, nunca bloquea aunque sea tarde y no haya plan")
    void antesDelDia31NuncaBloquea() {
        assertThat(BloqueoPlanificacion.bloqueada(30, 21, 0)).isFalse();
    }

    @Test
    @DisplayName("antes de las 20:00 local, no bloquea aunque falte plan")
    void antesDeLas20NoBloquea() {
        assertThat(BloqueoPlanificacion.bloqueada(31, 19, 0)).isFalse();
    }

    @Test
    @DisplayName("dia >= 31, hora >= 20 y menos de 3 rocas para manana -> bloqueada")
    void bloqueaCuandoFaltanRocasParaManana() {
        assertThat(BloqueoPlanificacion.bloqueada(31, 20, 0)).isTrue();
        assertThat(BloqueoPlanificacion.bloqueada(45, 23, 2)).isTrue();
    }

    @Test
    @DisplayName("con las 3 rocas de manana ya planificadas, no bloquea")
    void noBloqueaConLasTresRocasPlanificadas() {
        assertThat(BloqueoPlanificacion.bloqueada(31, 20, 3)).isFalse();
    }
}

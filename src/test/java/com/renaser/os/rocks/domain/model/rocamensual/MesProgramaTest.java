package com.renaser.os.rocks.domain.model.rocamensual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los tres tramos de 30 dias del programa. Lo que se prueba son los <b>bordes</b>: el dia 30 y el
 * 31, el 60 y el 61 — que es donde un {@code >=} de mas o de menos corre un mes entero y nadie lo
 * nota hasta que un aprendiz ve el objetivo del mes que no es.
 */
class MesProgramaTest {

    @Test
    @DisplayName("el dia 0 (todavia no arranco) cuenta como mes 1: 'mes 0' no significaria nada")
    void diaCeroEsMesUno() {
        assertThat(MesPrograma.deDia(0)).isEqualTo(1);
    }

    @Test
    @DisplayName("el primer mes va del dia 1 al 30, ambos inclusive")
    void primerMesLlegaHastaElDiaTreinta() {
        assertThat(MesPrograma.deDia(1)).isEqualTo(1);
        assertThat(MesPrograma.deDia(30)).isEqualTo(1);
    }

    @Test
    @DisplayName("el dia 31 ya es el segundo mes, y el 60 todavia lo es")
    void segundoMesVaDelTreintaYUnoAlSesenta() {
        assertThat(MesPrograma.deDia(31)).isEqualTo(2);
        assertThat(MesPrograma.deDia(60)).isEqualTo(2);
    }

    @Test
    @DisplayName("el dia 61 abre el tercer mes y el 90 lo cierra")
    void tercerMesVaDelSesentaYUnoAlNoventa() {
        assertThat(MesPrograma.deDia(61)).isEqualTo(3);
        assertThat(MesPrograma.deDia(90)).isEqualTo(3);
    }

    @Test
    @DisplayName("pasado el dia 90 se queda en el mes 3: el programa no tiene un cuarto mes")
    void masAlladelNoventaSigueSiendoElTercerMes() {
        assertThat(MesPrograma.deDia(120)).isEqualTo(3);
    }

    @Test
    @DisplayName("cada mes cierra al dia 30, 60 y 90 — no al 28, 56 y 84 de cuatro semanas")
    void cadaMesCierraDeTreintaEnTreinta() {
        assertThat(MesPrograma.ultimoDiaDe(1)).isEqualTo(30);
        assertThat(MesPrograma.ultimoDiaDe(2)).isEqualTo(60);
        assertThat(MesPrograma.ultimoDiaDe(3)).isEqualTo(90);
    }

    @Test
    @DisplayName("el primer dia de cada mes engancha con el ultimo del anterior, sin huecos")
    void losMesesNoDejanDiasHuerfanos() {
        assertThat(MesPrograma.primerDiaDe(1)).isEqualTo(1);
        assertThat(MesPrograma.primerDiaDe(2)).isEqualTo(MesPrograma.ultimoDiaDe(1) + 1);
        assertThat(MesPrograma.primerDiaDe(3)).isEqualTo(MesPrograma.ultimoDiaDe(2) + 1);
        assertThat(MesPrograma.ultimoDiaDe(MesPrograma.MESES)).isEqualTo(MesPrograma.ULTIMO_DIA);
    }

    @Test
    @DisplayName("solo 1, 2 y 3 son meses validos")
    void esValidoSoloDentroDeLosTresMeses() {
        assertThat(MesPrograma.esValido(0)).isFalse();
        assertThat(MesPrograma.esValido(1)).isTrue();
        assertThat(MesPrograma.esValido(3)).isTrue();
        assertThat(MesPrograma.esValido(4)).isFalse();
    }
}

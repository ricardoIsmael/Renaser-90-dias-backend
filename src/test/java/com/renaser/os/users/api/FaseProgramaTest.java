package com.renaser.os.users.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-66: verifica los cortes de dia->fase (1-7 / 8-34 / 35-64 / 65-90) contra los
 * confirmados en docs/MODULO_PHASECONTRACTS.md §1 y el espejo en
 * {@code phasecontracts.domain.model.contrato.FasePrograma.paraDiaPrograma} — los bordes
 * exactos (7/8, 34/35, 64/65) son donde se rompen estas reglas si alguien las toca.
 */
class FaseProgramaTest {

    @Test
    void diaCeroYUnoSonFaseUno() {
        assertThat(FasePrograma.paraDiaPrograma(0)).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
        assertThat(FasePrograma.paraDiaPrograma(1)).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
    }

    @Test
    void diaSieteEsElUltimoDeFaseUno() {
        assertThat(FasePrograma.paraDiaPrograma(7)).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
    }

    @Test
    void diaOchoEsElPrimeroDeFaseDos() {
        assertThat(FasePrograma.paraDiaPrograma(8)).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
    }

    @Test
    void diaTreintaYCuatroEsElUltimoDeFaseDos() {
        assertThat(FasePrograma.paraDiaPrograma(34)).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
    }

    @Test
    void diaTreintaYCincoEsElPrimeroDeFaseTres() {
        assertThat(FasePrograma.paraDiaPrograma(35)).isEqualTo(FasePrograma.PHASE_3_ALCHEMIST_WARRIOR);
    }

    @Test
    void diaSesentaYCuatroEsElUltimoDeFaseTres() {
        assertThat(FasePrograma.paraDiaPrograma(64)).isEqualTo(FasePrograma.PHASE_3_ALCHEMIST_WARRIOR);
    }

    @Test
    void diaSesentaYCincoEsElPrimeroDeFaseCuatro() {
        assertThat(FasePrograma.paraDiaPrograma(65)).isEqualTo(FasePrograma.PHASE_4_ASCENSION);
    }

    @Test
    void diaNoventaSigueSiendoFaseCuatro() {
        assertThat(FasePrograma.paraDiaPrograma(90)).isEqualTo(FasePrograma.PHASE_4_ASCENSION);
    }
}

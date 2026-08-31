package com.renaser.os.habits.domain.model.preferencia;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CuotaEdicionHorarioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 8, 24);

    @Test
    void losPrimerosSieteDiasSonSemanaDeAcomodoLibre() {
        assertThat(CuotaEdicionHorario.esSemanaDeAcomodoLibre(1)).isTrue();
        assertThat(CuotaEdicionHorario.esSemanaDeAcomodoLibre(7)).isTrue();
        assertThat(CuotaEdicionHorario.esSemanaDeAcomodoLibre(8)).isFalse();
    }

    @Test
    void laSemanaDeProgramaArrancaElDiaUnoDeCadaBloqueDeSiete() {
        assertThat(CuotaEdicionHorario.inicioSemanaPrograma(HOY, 8)).isEqualTo(HOY);        // dia 8 = dia 1 de semana 2
        assertThat(CuotaEdicionHorario.inicioSemanaPrograma(HOY, 10)).isEqualTo(HOY.minusDays(2));
        assertThat(CuotaEdicionHorario.inicioSemanaPrograma(HOY, 14)).isEqualTo(HOY.minusDays(6));
        assertThat(CuotaEdicionHorario.inicioSemanaPrograma(HOY, 15)).isEqualTo(HOY);
    }

    @Test
    void elDiaCeroSeTrataComoElPrimerDiaDeLaSemana() {
        assertThat(CuotaEdicionHorario.inicioSemanaPrograma(HOY, 0)).isEqualTo(HOY);
    }

    @Test
    void losRestantesNuncaBajanDeCero() {
        assertThat(CuotaEdicionHorario.de(5, false).restantes()).isZero();
        assertThat(CuotaEdicionHorario.de(1, false).restantes()).isEqualTo(2);
        assertThat(CuotaEdicionHorario.de(0, false).limite()).isEqualTo(CuotaEdicionHorario.HABITOS_POR_SEMANA);
    }

    @Test
    void elPeriodoUsaLosLiteralesDelContratoViejo() {
        assertThat(CuotaEdicionHorario.de(0, true).periodo()).isEqualTo("FREE");
        assertThat(CuotaEdicionHorario.de(0, false).periodo()).isEqualTo("WEEK");
    }
}

package com.renaser.os.phasecontracts.domain.model.contrato;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaseProgramaTest {

    @DisplayName("paraDiaPrograma: cortes 1 / 8 / 35 / 65 (guias, no el avatar)")
    @ParameterizedTest(name = "dia {0} -> {1}")
    @CsvSource({
            "1,  FASE_1_RENACER",
            "2,  FASE_1_RENACER",
            "7,  FASE_1_RENACER",
            "8,  FASE_2_DESARROLLO",
            "16, FASE_2_DESARROLLO",
            "17, FASE_2_DESARROLLO",
            "34, FASE_2_DESARROLLO",
            "35, FASE_3_GUERRERO_ALQUIMISTA",
            "64, FASE_3_GUERRERO_ALQUIMISTA",
            "65, FASE_4_ASCENSION",
            "90, FASE_4_ASCENSION",
    })
    void paraDiaProgramaUsaLosCortesDeLasGuias(int diaPrograma, FasePrograma esperada) {
        assertThat(FasePrograma.paraDiaPrograma(diaPrograma)).isEqualTo(esperada);
    }

    @DisplayName("dia 0 (participante recien aprobado, todavia no arranco) cae en Fase I")
    void diaCeroEsFaseUno() {
        assertThat(FasePrograma.paraDiaPrograma(0)).isEqualTo(FasePrograma.FASE_1_RENACER);
    }

    @DisplayName("Fase I nunca se desbloquea para firmar aqui (se firma en el Pacto del onboarding)")
    @ParameterizedTest(name = "dia {0}")
    @CsvSource({"1", "5", "7"})
    void faseUnoNuncaDesbloqueada(int diaPrograma) {
        assertThat(FasePrograma.FASE_1_RENACER.firmaDesbloqueadaEnDia(diaPrograma)).isFalse();
        assertThat(FasePrograma.faseAFirmarEnDia(diaPrograma)).isNull();
    }

    @DisplayName("Fase II: arranca dia 8 pero se firma recien el dia 17 (NO coinciden)")
    @ParameterizedTest(name = "dia {0} -> desbloqueada={1}")
    @CsvSource({
            "8,  false",
            "10, false",
            "16, false",
            "17, true",
            "18, true",
            "34, true",
    })
    void faseDosSeDesbloqueaElDia17(int diaPrograma, boolean desbloqueada) {
        assertThat(FasePrograma.FASE_2_DESARROLLO.firmaDesbloqueadaEnDia(diaPrograma)).isEqualTo(desbloqueada);
    }

    @DisplayName("Fase III: arranca y se firma el mismo dia, 35 (coincidencia declarada aparte a proposito)")
    @ParameterizedTest(name = "dia {0} -> desbloqueada={1}")
    @CsvSource({"34, false", "35, true", "50, true"})
    void faseTresSeDesbloqueaElDia35(int diaPrograma, boolean desbloqueada) {
        assertThat(FasePrograma.FASE_3_GUERRERO_ALQUIMISTA.firmaDesbloqueadaEnDia(diaPrograma)).isEqualTo(desbloqueada);
    }

    @DisplayName("Fase IV: arranca y se firma el mismo dia, 65")
    @ParameterizedTest(name = "dia {0} -> desbloqueada={1}")
    @CsvSource({"64, false", "65, true", "90, true"})
    void faseCuatroSeDesbloqueaElDia65(int diaPrograma, boolean desbloqueada) {
        assertThat(FasePrograma.FASE_4_ASCENSION.firmaDesbloqueadaEnDia(diaPrograma)).isEqualTo(desbloqueada);
    }

    @DisplayName("faseAFirmarEnDia: la fase EN CURSO, solo si ya paso su dia de desbloqueo")
    @ParameterizedTest(name = "dia {0} -> {1}")
    @CsvSource({
            "5,  ''",
            "10, ''",
            "17, FASE_2_DESARROLLO",
            "30, FASE_2_DESARROLLO",
            "35, FASE_3_GUERRERO_ALQUIMISTA",
            "65, FASE_4_ASCENSION",
            "90, FASE_4_ASCENSION",
    })
    void faseAFirmarEnDiaDaLaFaseEnCursoSoloSiYaLeToca(int diaPrograma, String esperadaTexto) {
        FasePrograma esperada = esperadaTexto.isEmpty() ? null : FasePrograma.valueOf(esperadaTexto);
        assertThat(FasePrograma.faseAFirmarEnDia(diaPrograma)).isEqualTo(esperada);
    }

    @DisplayName("numero() es 1..4, independiente del ordinal del enum")
    @ParameterizedTest
    @EnumSource(FasePrograma.class)
    void numeroPositivo(FasePrograma fase) {
        assertThat(fase.numero()).isBetween(1, 4);
        assertThat(FasePrograma.porNumero(fase.numero())).isEqualTo(fase);
    }

    @DisplayName("porNumero rechaza numeros fuera de 1..4")
    void porNumeroRechazaInvalidos() {
        assertThatThrownBy(() -> FasePrograma.porNumero(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FasePrograma.porNumero(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("etiqueta() no es null ni vacia para ninguna fase")
    @ParameterizedTest
    @EnumSource(FasePrograma.class)
    void etiquetaSiempreDefinida(FasePrograma fase) {
        assertThat(fase.etiqueta()).isNotBlank();
    }
}

package com.renaser.os.habits.domain.model.medicion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-226: lo que la columna {@code registros_habito.valor_medido numeric(7,2) >= 0} puede guardar. */
class MedicionDiariaTest {

    @Test
    @DisplayName("se guarda con dos decimales, redondeando al más cercano (3,456 km → 3,46)")
    void redondeaADosDecimales() {
        assertThat(MedicionDiaria.manualSiHay(new BigDecimal("3.456")).valor()).isEqualByComparingTo("3.46");
        assertThat(MedicionDiaria.manualSiHay(new BigDecimal("3.454")).valor()).isEqualByComparingTo("3.45");
        assertThat(MedicionDiaria.manualSiHay(new BigDecimal("3.455")).valor()).isEqualByComparingTo("3.46");
        assertThat(MedicionDiaria.manualSiHay(new BigDecimal("7")).valor().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("sin número no hay medición; con número es MANUAL")
    void manualSiHay() {
        assertThat(MedicionDiaria.manualSiHay(null)).isNull();
        assertThat(MedicionDiaria.manualSiHay(BigDecimal.ONE).origen()).isEqualTo(OrigenMedicion.MANUAL);
    }

    @Test
    @DisplayName("no acepta negativos ni lo que no entra en numeric(7,2)")
    void rechazaLoQueLaColumnaNoGuarda() {
        assertThatThrownBy(() -> MedicionDiaria.manualSiHay(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MedicionDiaria.manualSiHay(new BigDecimal("100000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MedicionDiaria.manualSiHay(new BigDecimal("99999.99")).valor()).isEqualByComparingTo("99999.99");
    }

    @Test
    @DisplayName("cero es representable: si vale para el hábito lo decide su política, no el número")
    void ceroEsRepresentable() {
        assertThat(MedicionDiaria.manualSiHay(new BigDecimal("0.001")).valor()).isEqualByComparingTo("0.00");
    }
}

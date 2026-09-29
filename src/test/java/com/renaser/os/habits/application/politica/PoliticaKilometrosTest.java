package com.renaser.os.habits.application.politica;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.habits.domain.model.politica.ContextoCompletar;
import com.renaser.os.habits.domain.model.politica.DecisionPolitica;
import com.renaser.os.habits.domain.model.politica.SelectorHabito;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** D-226: KILÓMETROS DIARIOS se cumple con cualquier km mayor que cero, hasta el tope por día. */
class PoliticaKilometrosTest {

    private final PoliticaKilometros politica = new PoliticaKilometros();
    /** La política no mira el hábito: basta con que no sea null en la firma. */
    private final Habito habito = null;

    private DecisionPolitica con(String km) {
        return politica.puedeCompletarseDirecto(habito,
                ContextoCompletar.conMedicion(MedicionDiaria.manualSiHay(new BigDecimal(km))));
    }

    @Test
    @DisplayName("selecciona el hábito DAILY_KM y mide en kilómetros")
    void seleccionaDailyKmEnKilometros() {
        assertThat(politica.selector()).isEqualTo(SelectorHabito.porClave("DAILY_KM"));
        assertThat(politica.unidadDeMedicion()).contains(UnidadMedicion.KILOMETROS);
    }

    @Test
    @DisplayName("sin km no se completa, y el motivo dice qué falta (también a quien tiene la app vieja)")
    void sinKmNoProcede() {
        DecisionPolitica decision = politica.puedeCompletarseDirecto(habito, ContextoCompletar.sinHechosExternos());

        assertThat(decision).isInstanceOfSatisfying(DecisionPolitica.NoProcede.class,
                no -> assertThat(no.motivo()).contains("km").contains("actualiza la app"));
    }

    @ParameterizedTest(name = "{0} km no procede")
    @ValueSource(strings = {"0", "0.00", "0.004", "100.01", "150", "330"})
    @DisplayName("cero (también lo que redondea a cero) y más del tope no proceden")
    void ceroYMasDelTopeNoProceden(String km) {
        assertThat(con(km)).isInstanceOf(DecisionPolitica.NoProcede.class);
    }

    @ParameterizedTest(name = "{0} km procede")
    @ValueSource(strings = {"0.01", "0.005", "3.5", "42.195", "100", "100.004"})
    @DisplayName("cualquier km mayor que cero hasta 100 (tras redondear a dos decimales) procede")
    void mayorQueCeroHastaElTopeProcede(String km) {
        assertThat(con(km)).isInstanceOf(DecisionPolitica.Procede.class);
    }

    @Test
    @DisplayName("el tope es 100 km por día (supuesto a confirmar con el dueño)")
    void elTopeEsCien() {
        assertThat(PoliticaKilometros.TOPE_KM_POR_DIA).isEqualByComparingTo("100");
    }
}

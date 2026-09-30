package com.renaser.os.rag.domain.model.mapa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** D-233: el proximo hito del Mapa (30, 60, 90) y cuantos dias faltan, desde el dia del programa de hoy. */
class ProximoHitoTest {

    @ParameterizedTest(name = "dia {0} -> hito {1}, faltan {2}")
    @DisplayName("el proximo es el primero que no paso; el mismo dia del hito cuenta como hoy")
    @CsvSource({"1,30,29", "7,30,23", "29,30,1", "30,30,0", "31,60,29", "60,60,0", "61,90,29", "90,90,0"})
    void proximo(int dia, int hito, int faltan) {
        assertThat(ProximoHito.para(dia)).contains(new ProximoHito(hito, faltan));
    }

    @ParameterizedTest(name = "dia {0}")
    @DisplayName("antes del dia 1 o despues del 90 no hay proximo hito")
    @ValueSource(ints = {-1, 0, 91})
    void sinProximo(int dia) {
        assertThat(ProximoHito.para(dia)).isEmpty();
    }
}

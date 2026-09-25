package com.renaser.os.habits.domain.model.habito;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los Ciclos de Intoxicacion Consciente (D-169): dias 8-10 (VER), 17-19 (CORTAR) y 26-28
 * (RENASER). Sin Spring y sin base: la regla es una funcion pura del dia de programa.
 *
 * <p>El dia que recibe ya viene derivado de las fechas en la zona del participante y con
 * {@code dias_ajuste_programa} descontado; ese tramo (zona, hora UTC del dia anterior, ajuste
 * positivo y negativo) se prueba de punta a punta en {@code CicloIntoxicacionGeneracionIT}.
 */
class CicloIntoxicacionTest {

    @ParameterizedTest(name = "dia {0} es de intoxicacion")
    @ValueSource(ints = {8, 9, 10, 17, 18, 19, 26, 27, 28})
    @DisplayName("los nueve dias de las tres ventanas son de intoxicacion")
    void losDiasDeLasTresVentanasSonDeIntoxicacion(int dia) {
        assertThat(CicloIntoxicacion.esDiaDeIntoxicacion(dia)).isTrue();
    }

    /** Los bordes de cada ventana, de los dos lados: 7/8, 10/11, 16/17, 19/20, 25/26 y 28/29. */
    @ParameterizedTest(name = "dia {0} NO es de intoxicacion")
    @ValueSource(ints = {7, 11, 16, 20, 25, 29})
    @DisplayName("el dia de antes y el de despues de cada ventana son dias normales")
    void losBordesDeAfueraSonDiasNormales(int dia) {
        assertThat(CicloIntoxicacion.esDiaDeIntoxicacion(dia)).isFalse();
    }

    @ParameterizedTest(name = "dia {0} NO es de intoxicacion")
    @ValueSource(ints = {0, -1, 1, 90, 91, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("el dia 0, los extremos del programa y lo que cae fuera de 1..90 nunca son de intoxicacion")
    void fueraDeLasVentanasNuncaEsDeIntoxicacion(int dia) {
        assertThat(CicloIntoxicacion.esDiaDeIntoxicacion(dia)).isFalse();
    }

    @Test
    @DisplayName("en los 90 dias del programa hay exactamente nueve dias de intoxicacion")
    void elProgramaTieneNueveDiasDeIntoxicacion() {
        long dias = IntStream.rangeClosed(1, 90).filter(CicloIntoxicacion::esDiaDeIntoxicacion).count();

        assertThat(dias).isEqualTo(9);
    }
}

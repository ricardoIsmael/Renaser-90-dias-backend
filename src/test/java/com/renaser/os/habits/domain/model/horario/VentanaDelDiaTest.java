package com.renaser.os.habits.domain.model.horario;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D-122 (2026-09-08). La ventana de un habito tiene que caber SIEMPRE dentro del dia calendario.
 *
 * <p>El caso real detras: aprendices que trabajan de noche o de madrugada. La opcion de dejar que
 * la ventana cruzara la medianoche se descarto por decision del dueño —<i>"a las 12 ya es otro
 * dia"</i>— asi que en vez de permitir el cruce se acota la hora de arranque y se acomoda la de
 * cierre.
 */
class VentanaDelDiaTest {

    @Test
    void laHoraDeDisparoLlegaHastaLas2340() {
        assertThat(VentanaDelDia.requireHoraDisparoDentroDelDia(LocalTime.of(23, 40)))
                .isEqualTo(LocalTime.of(23, 40));
    }

    @Test
    void masTardeDeLas2340NoSePuedeEmpezar() {
        assertThatThrownBy(() -> VentanaDelDia.requireHoraDisparoDentroDelDia(LocalTime.of(23, 41)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("23:40");
    }

    /** "Todo el dia": es como viaja casi todo el catalogo, y sigue valiendo. */
    @Test
    void sinHoraDeDisparoNoHayNadaQueAcotar() {
        assertThat(VentanaDelDia.requireHoraDisparoDentroDelDia(null)).isNull();
        assertThat(VentanaDelDia.horaLimiteAjustada(null, null)).isNull();
    }

    @Test
    void unaVentanaNormalNoSeToca() {
        assertThat(VentanaDelDia.horaLimiteAjustada(LocalTime.of(6, 0), LocalTime.of(7, 30)))
                .isEqualTo(LocalTime.of(7, 30));
    }

    /**
     * El caso del turno noche: se elige empezar a las 23:00 y cerrar a las 00:30. Antes de D-122
     * esto era un 400 ("horaLimite debe ser posterior a horaDisparo") y el aprendiz quedaba
     * trabado sin saber por que. Ahora se acomoda al ultimo instante util del dia.
     */
    @Test
    void unCierrePasadaLaMedianocheSeAcomodaAlUltimoInstanteDelDia() {
        assertThat(VentanaDelDia.horaLimiteAjustada(LocalTime.of(23, 0), LocalTime.of(0, 30)))
                .isEqualTo(LocalTime.of(23, 50));
    }

    @Test
    void unCierreDespuesDeLas2350SeAcotaAhi() {
        assertThat(VentanaDelDia.horaLimiteAjustada(LocalTime.of(23, 0), LocalTime.of(23, 59)))
                .isEqualTo(LocalTime.of(23, 50));
    }

    @Test
    void unCierreIgualAlArranqueDejaDeSerUnaVentanaVacia() {
        assertThat(VentanaDelDia.horaLimiteAjustada(LocalTime.of(23, 0), LocalTime.of(23, 0)))
                .isEqualTo(LocalTime.of(23, 50));
    }

    /**
     * El peor caso posible sigue dejando 10 minutos para completar — que es exactamente el numero
     * que fijo el dueño, y la razon de que el tope de arranque sea 23:40 y no las 23:00.
     */
    @Test
    void elPeorCasoDejaDiezMinutosParaCompletar() {
        LocalTime cierre = VentanaDelDia.horaLimiteAjustada(VentanaDelDia.ULTIMA_HORA_DE_DISPARO,
                LocalTime.of(0, 5));

        assertThat(java.time.Duration.between(VentanaDelDia.ULTIMA_HORA_DE_DISPARO, cierre).toMinutes())
                .isEqualTo(10);
    }
}

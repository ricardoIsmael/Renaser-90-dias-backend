package com.renaser.os.habits.domain.model.preferencia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 009 (D-283): lo que UNA capa (el habito o un dia de la semana) dice de como suena la alarma. Cada
 * campo en {@code null} es «no digo nada, hereda»: se resuelve campo por campo, igual que la hora y el aviso.
 */
class AjusteDeAlarmaTest {

    private static final CombinacionDeAlarma HOY = CombinacionDeAlarma.COMO_HOY;

    @Test
    void unAjusteQueNoDiceNadaHeredaTodo() {
        var nada = AjusteDeAlarma.hereda();

        assertThat(nada.esVacio()).isTrue();
        assertThat(new AjusteDeAlarma(null, null, null, null)).isEqualTo(nada);
        assertThat(nada.aplicadoA(HOY)).isEqualTo(HOY);
    }

    @Test
    void deUnaCombinacionFijaLosCuatroCampos() {
        var combinacion = CombinacionDeAlarma.de(false, true, false, true);

        var ajuste = AjusteDeAlarma.de(combinacion);

        assertThat(ajuste.esVacio()).isFalse();
        assertThat(ajuste.sonido()).isFalse();
        assertThat(ajuste.vibracion()).isTrue();
        assertThat(ajuste.voz()).isFalse();
        assertThat(ajuste.repetir()).isTrue();
        assertThat(ajuste.aplicadoA(HOY)).isEqualTo(combinacion);
    }

    /** La herencia es por CAMPO: la capa que solo dice «repetir» deja el resto a la de abajo. */
    @Test
    void laHerenciaEsPorCampo() {
        var general = CombinacionDeAlarma.de(true, false, true, false);

        var sabado = new AjusteDeAlarma(null, null, null, true).aplicadoA(general);

        assertThat(sabado).isEqualTo(CombinacionDeAlarma.de(true, false, true, true));
    }

    @Test
    void sonidoYVibracionApagadosEnLaMismaCapaSeRechazan() {
        assertThatThrownBy(() -> new AjusteDeAlarma(false, false, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sonido o la vibracion");
    }

    @Test
    void laVozEncendidaConSonidoApagadoEnLaMismaCapaSeRechaza() {
        assertThatThrownBy(() -> new AjusteDeAlarma(false, true, true, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("voz");
    }

    /** La voz es un sonido: si la capa de arriba lo apaga, la voz que venia de abajo se apaga con el. */
    @Test
    void vozSinSonidoAlResolverQuedaApagada() {
        var general = CombinacionDeAlarma.de(true, true, true, false);

        var dia = new AjusteDeAlarma(false, null, null, null).aplicadoA(general);

        assertThat(dia.sonido()).isFalse();
        assertThat(dia.voz()).as("voz sin sonido -> apagada").isFalse();
        assertThat(dia.vibracion()).isTrue();
    }

    /**
     * Una capa que apaga el sonido sobre otra que ya tenia la vibracion apagada dejaria las dos en no: esa
     * mezcla no puede pasar (la persona se quedaria sin alarma). Manda lo de abajo en esos dos campos.
     */
    @Test
    void sonidoYVibracionApagadosPorLaMezclaGanaLoDeAbajo() {
        var general = CombinacionDeAlarma.de(true, false, false, false);

        var dia = new AjusteDeAlarma(false, null, null, true).aplicadoA(general);

        assertThat(dia.sonido()).isTrue();
        assertThat(dia.vibracion()).isFalse();
        assertThat(dia.repetir()).as("lo demas que dice la capa si se aplica").isTrue();
    }
}

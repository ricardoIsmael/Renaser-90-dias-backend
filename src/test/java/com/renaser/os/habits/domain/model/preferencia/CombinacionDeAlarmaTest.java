package com.renaser.os.habits.domain.model.preferencia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 009 (D-283): como suena la alarma de un habito. Es la combinacion YA resuelta y valida: las cuatro
 * respuestas existen y las dos reglas se cumplen. Lo que una capa deja sin decir es {@link AjusteDeAlarma}.
 */
class CombinacionDeAlarmaTest {

    /** El valor por defecto es lo que suena HOY: tono y vibracion, sin voz propia y sin repetir. */
    @Test
    void laDeHoyEsSonidoYVibracionSinVozNiRepeticion() {
        var hoy = CombinacionDeAlarma.COMO_HOY;

        assertThat(hoy.sonido()).isTrue();
        assertThat(hoy.vibracion()).isTrue();
        assertThat(hoy.voz()).isFalse();
        assertThat(hoy.repetir()).isFalse();
    }

    @Test
    void sonidoYVibracionNoPuedenIrLosDosApagados() {
        assertThatThrownBy(() -> CombinacionDeAlarma.de(false, false, false, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sonido o la vibracion");
    }

    @Test
    void laVozNecesitaElSonido() {
        assertThatThrownBy(() -> CombinacionDeAlarma.de(false, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("voz");
    }

    @Test
    void soloVibrarYSoloSonarSonValidas() {
        var vibrar = CombinacionDeAlarma.de(false, true, false, false);
        var sonar = CombinacionDeAlarma.de(true, false, true, true);

        assertThat(vibrar.sonido()).isFalse();
        assertThat(vibrar.vibracion()).isTrue();
        assertThat(sonar.vibracion()).isFalse();
        assertThat(sonar.voz()).isTrue();
        assertThat(sonar.repetir()).isTrue();
    }

    @Test
    void repetirEsIndependienteDeLoDemas() {
        assertThat(CombinacionDeAlarma.de(false, true, false, true).repetir()).isTrue();
        assertThat(CombinacionDeAlarma.de(true, true, true, false).repetir()).isFalse();
    }

    @Test
    void dosCombinacionesIgualesSonIguales() {
        assertThat(CombinacionDeAlarma.de(true, true, false, false)).isEqualTo(CombinacionDeAlarma.COMO_HOY);
    }

    /** {@code con} pone encima lo que fija una capa y deja el resto como estaba. */
    @Test
    void conPoneLoQueFijaLaCapaYDejaElResto() {
        var base = CombinacionDeAlarma.de(true, true, true, false);

        var resultado = base.con(new AjusteDeAlarma(null, null, null, true));

        assertThat(resultado).isEqualTo(CombinacionDeAlarma.de(true, true, true, true));
    }

    @Test
    void conUnaCapaQueHeredaTodoOUnaCapaNulaDevuelveLaMisma() {
        var base = CombinacionDeAlarma.de(false, true, false, true);

        assertThat(base.con(AjusteDeAlarma.hereda())).isEqualTo(base);
        assertThat(base.con(null)).isEqualTo(base);
    }
}

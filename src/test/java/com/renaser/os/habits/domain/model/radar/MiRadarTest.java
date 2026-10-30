package com.renaser.os.habits.domain.model.radar;

import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 019: Mi Radar guarda solo si esta encendido y, por dia de la semana, lo que no es de fabrica. */
class MiRadarTest {

    @Test
    @DisplayName("apagado y con todos los dias de fabrica: Automatico y con Radar")
    void apagado() {
        MiRadar miRadar = MiRadar.apagado();

        assertThat(miRadar.encendido()).isFalse();
        assertThat(miRadar.dias()).isEmpty();
        assertThat(miRadar.dia(DayOfWeek.SATURDAY)).isEqualTo(DiaDeMiRadar.normal(6));
    }

    @Test
    @DisplayName("un dia de fabrica no se guarda: los dias quedan ordenados y solo con lo distinto")
    void soloLoDistinto() {
        MiRadar miRadar = new MiRadar(true, List.of(new DiaDeMiRadar(7, Modo.AUTOMATICO, null, true),
                DiaDeMiRadar.normal(3), new DiaDeMiRadar(2, Modo.PERSONALIZADO, 4, false)));

        assertThat(miRadar.dias()).extracting(DiaDeMiRadar::dia).containsExactly(2, 7);
        assertThat(miRadar.dia(2).veces()).isEqualTo(4);
        assertThat(miRadar.dia(7).sinRadar()).isTrue();
        assertThat(miRadar.dia(3)).isEqualTo(DiaDeMiRadar.normal(3));
    }

    @Test
    @DisplayName("Automatico no guarda veces y Personalizado sin veces propone 6")
    void veces() {
        assertThat(new DiaDeMiRadar(1, Modo.AUTOMATICO, 9, false).veces()).isNull();
        assertThat(new DiaDeMiRadar(1, Modo.PERSONALIZADO, null, false).veces()).isEqualTo(DiaDeMiRadar.VECES_POR_DEFECTO);
    }

    @Test
    @DisplayName("las veces van de 1 a 21 y el dia de 1 a 7, sin repetir: lo demas es un 400")
    void rechazaLoQueNoCumple() {
        assertThatThrownBy(() -> new DiaDeMiRadar(1, Modo.PERSONALIZADO, 0, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Las veces por día van de 1 a 21");
        assertThatThrownBy(() -> new DiaDeMiRadar(1, Modo.PERSONALIZADO, 22, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiaDeMiRadar(0, Modo.AUTOMATICO, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiaDeMiRadar(8, Modo.AUTOMATICO, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MiRadar(true, List.of(new DiaDeMiRadar(2, Modo.AUTOMATICO, null, true),
                new DiaDeMiRadar(2, Modo.PERSONALIZADO, 3, false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("No repitas el mismo día");
    }

    @Test
    @DisplayName("con() cambia solo lo que llega: lo demas se queda como estaba")
    void conSoloLoQueLlega() {
        DiaDeMiRadar base = new DiaDeMiRadar(6, Modo.PERSONALIZADO, 3, false);

        assertThat(base.con(null, 5, null)).isEqualTo(new DiaDeMiRadar(6, Modo.PERSONALIZADO, 5, false));
        assertThat(base.con(null, null, true)).isEqualTo(new DiaDeMiRadar(6, Modo.PERSONALIZADO, 3, true));
        assertThat(base.con(Modo.AUTOMATICO, null, null)).isEqualTo(DiaDeMiRadar.normal(6));
    }

    @Test
    @DisplayName("conDias reemplaza los que llegan, conserva el resto y vuelve a los de fabrica al dejar un dia normal")
    void conDias() {
        MiRadar miRadar = new MiRadar(true, List.of(new DiaDeMiRadar(2, Modo.PERSONALIZADO, 4, false),
                new DiaDeMiRadar(6, Modo.AUTOMATICO, null, true)));

        MiRadar nuevo = miRadar.conDias(List.of(DiaDeMiRadar.normal(6), new DiaDeMiRadar(7, Modo.PERSONALIZADO, 2, false)));

        assertThat(nuevo.dias()).extracting(DiaDeMiRadar::dia).containsExactly(2, 7);
        assertThat(nuevo.encendido()).isTrue();
    }

    @Test
    @DisplayName("apagar no borra los dias: encender de nuevo los encuentra como estaban")
    void apagarConservaLosDias() {
        MiRadar miRadar = new MiRadar(true, List.of(new DiaDeMiRadar(6, Modo.PERSONALIZADO, 4, false)));

        MiRadar apagado = miRadar.conEncendido(false);

        assertThat(apagado.encendido()).isFalse();
        assertThat(apagado.dia(6).veces()).isEqualTo(4);
    }
}

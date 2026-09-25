package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CumplimientoDelDiaTest {

    private static final LocalDate DIA = LocalDate.of(2026, 9, 22);

    /** El ejemplo del dueño: 7 de 9 hábitos que contaban + 2 de 3 objetivos = 9 de 12 = 75 %. */
    @Test
    void sumaHabitosYObjetivosDelDia() {
        CumplimientoDelDia dia = CumplimientoDelDia.de(DIA, new ConteoDelDia(DIA, 9, 7), new ConteoDelDia(DIA, 3, 2));

        assertThat(dia.programados()).isEqualTo(12);
        assertThat(dia.cumplidos()).isEqualTo(9);
        assertThat(dia.porcentaje()).contains(75);
        DiaDelSemaforo expuesto = dia.aDia();
        assertThat(expuesto.estado()).isEqualTo(EstadoDiaSemaforo.MEDIDO);
        assertThat(expuesto.color()).isEqualTo(ColorSemaforo.AMARILLO);
        assertThat(expuesto.habitosProgramados()).isEqualTo(9);
        assertThat(expuesto.objetivosCumplidos()).isEqualTo(2);
    }

    /** Un objetivo planificado y no cumplido baja el %, aunque todos los hábitos estén hechos. */
    @Test
    void unObjetivoNoCumplidoCuentaComoPendiente() {
        CumplimientoDelDia dia = CumplimientoDelDia.de(DIA, new ConteoDelDia(DIA, 4, 4), new ConteoDelDia(DIA, 1, 0));

        assertThat(dia.porcentaje()).contains(80);
    }

    @Test
    void sinObjetivosPlanificadosSoloCuentanLosHabitos() {
        CumplimientoDelDia dia = CumplimientoDelDia.de(DIA, new ConteoDelDia(DIA, 5, 3), null);

        assertThat(dia.porcentaje()).contains(60);
    }

    @Test
    void unDiaSinNadaProgramadoEsSinDatosYNoUnCero() {
        CumplimientoDelDia dia = CumplimientoDelDia.de(DIA, null, null);

        assertThat(dia.conDatos()).isFalse();
        assertThat(dia.porcentaje()).isEmpty();
        assertThat(dia.aDia().estado()).isEqualTo(EstadoDiaSemaforo.SIN_DATOS);
        assertThat(dia.aDia().porcentaje()).isNull();
        assertThat(dia.aDia().color()).isEqualTo(ColorSemaforo.SIN_DATOS);
    }

    @Test
    void noAceptaConteosIncoherentes() {
        assertThatThrownBy(() -> new CumplimientoDelDia(DIA, 2, 3, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CumplimientoDelDia(DIA, 0, 0, -1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

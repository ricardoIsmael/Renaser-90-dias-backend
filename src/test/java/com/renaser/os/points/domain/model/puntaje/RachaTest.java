package com.renaser.os.points.domain.model.puntaje;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La racha derivada: días seguidos con al menos un hábito cumplido.
 *
 * <p>Dominio puro: se construye con {@code Racha.derivarDe(...)} a secas, sin contexto de Spring y
 * sin base de datos, como exige {@code .claude/rules/03}.
 */
class RachaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 14);

    @Test
    @DisplayName("un día cuenta una vez, aunque haya cumplido varios hábitos: es por día, no por hábito")
    void porDiaNoPorHabito() {
        // Ésta es la regla que el dueño pidió explícitamente y el bug que quería evitar: que la
        // racha subiera de a uno por cada hábito marcado.
        var tresHabitosElMismoDia = List.of(HOY, HOY, HOY);
        assertThat(Racha.derivarDe(tresHabitosElMismoDia, HOY).actual()).isEqualTo(1);
    }

    @Test
    @DisplayName("días consecutivos se acumulan")
    void seAcumulan() {
        var cuatroSeguidos = Set.of(HOY, HOY.minusDays(1), HOY.minusDays(2), HOY.minusDays(3));
        assertThat(Racha.derivarDe(cuatroSeguidos, HOY).actual()).isEqualTo(4);
    }

    @Test
    @DisplayName("el día en curso, todavía sin cumplir, NO rompe la racha")
    void elDiaEnCursoNoRompe() {
        // A las 7 de la mañana nadie cumplió nada todavía. Si la racha contara sólo hasta hoy,
        // todo el padrón vería 0 cada mañana y el número dejaría de significar algo.
        var hastaAyer = Set.of(HOY.minusDays(1), HOY.minusDays(2), HOY.minusDays(3));
        assertThat(Racha.derivarDe(hastaAyer, HOY).actual()).isEqualTo(3);
    }

    @Test
    @DisplayName("un día salteado corta la racha")
    void unDiaSalteadoCorta() {
        // Cumplió hoy y ayer, pero antes se salteó el antepenúltimo: la vigente son 2.
        var conHueco = Set.of(HOY, HOY.minusDays(1), HOY.minusDays(3), HOY.minusDays(4));
        assertThat(Racha.derivarDe(conHueco, HOY).actual()).isEqualTo(2);
    }

    @Test
    @DisplayName("si el último día cumplido fue anteayer, la racha vigente es 0")
    void dosDiasSinCumplirLaCorta() {
        var viejo = Set.of(HOY.minusDays(2), HOY.minusDays(3), HOY.minusDays(4));
        assertThat(Racha.derivarDe(viejo, HOY).actual()).isZero();
    }

    @Test
    @DisplayName("la máxima recuerda el mejor tramo aunque la vigente se haya cortado")
    void laMaximaSobreviveAlCorte() {
        var historia = Set.of(
                HOY.minusDays(10), HOY.minusDays(9), HOY.minusDays(8), HOY.minusDays(7), HOY.minusDays(6),
                HOY, HOY.minusDays(1));
        Racha racha = Racha.derivarDe(historia, HOY);
        assertThat(racha.actual()).isEqualTo(2);
        assertThat(racha.maxima()).isEqualTo(5);
    }

    @Test
    @DisplayName("es idempotente: calcularla dos veces da lo mismo")
    void idempotente() {
        // La propiedad que motiva derivar en vez de acumular (E-91). Con un contador, llamar dos
        // veces sumaría dos.
        var dias = Set.of(HOY, HOY.minusDays(1));
        assertThat(Racha.derivarDe(dias, HOY)).isEqualTo(Racha.derivarDe(dias, HOY));
    }

    @Test
    @DisplayName("no se desfasa si el cálculo no corrió durante días")
    void noSeDesfasaSiNoCorrio() {
        // Éste es el test que falla contra un contador incrementado por cron: si el proceso no
        // corrió los días 12 y 13, un contador quedaría dos días atrás para siempre. Derivada, la
        // racha del día 14 es la correcta sin importar cuándo se calculó por última vez.
        var cumplioTodosLosDias = Set.of(HOY, HOY.minusDays(1), HOY.minusDays(2), HOY.minusDays(3));
        assertThat(Racha.derivarDe(cumplioTodosLosDias, HOY).actual()).isEqualTo(4);
    }

    @Test
    @DisplayName("sin actividad no inventa racha")
    void sinActividad() {
        assertThat(Racha.derivarDe(Set.of(), HOY)).isEqualTo(Racha.NINGUNA);
        assertThat(Racha.derivarDe(null, HOY)).isEqualTo(Racha.NINGUNA);
    }

    @Test
    @DisplayName("una fecha futura es un dato corrupto, no una racha")
    void ignoraFechasFuturas() {
        // Si el servidor devolviera una fecha adelantada (zona mal resuelta en el origen), contarla
        // inflaría la racha en silencio. Se descarta.
        var conFuturo = Set.of(HOY.plusDays(1), HOY, HOY.minusDays(1));
        assertThat(Racha.derivarDe(conFuturo, HOY).actual()).isEqualTo(2);
    }

    @Test
    @DisplayName("el orden en que llegan las fechas no cambia el resultado")
    void elOrdenNoImporta() {
        var desordenadas = List.of(HOY.minusDays(2), HOY, HOY.minusDays(1));
        assertThat(Racha.derivarDe(desordenadas, HOY).actual()).isEqualTo(3);
    }

    @Test
    @DisplayName("el invariante se defiende: no hay racha negativa ni actual mayor que la máxima")
    void invariantes() {
        assertThatThrownBy(() -> new Racha(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Racha(5, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cruza el cambio de mes y de año sin romperse")
    void cruzaMesYAnio() {
        LocalDate primeroDeEnero = LocalDate.of(2027, 1, 1);
        var findeDeAnio = Set.of(
                primeroDeEnero, primeroDeEnero.minusDays(1), primeroDeEnero.minusDays(2));
        assertThat(Racha.derivarDe(findeDeAnio, primeroDeEnero).actual()).isEqualTo(3);
    }
}

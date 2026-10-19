package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.HabitoDelDiaResumen;
import com.renaser.os.points.api.RocaDelDiaResumen;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El cumplimiento de hoy que {@code /home} le pasa al mensaje del semáforo (D-277). */
class CumplimientoDeHoyTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 19);

    private static HabitoDelDiaResumen habito(String estado, boolean cuenta) {
        return new HabitoDelDiaResumen(UUID.randomUUID(), "Hábito", estado, cuenta);
    }

    private static RocaDelDiaResumen objetivo(boolean completada) {
        return new RocaDelDiaResumen(UUID.randomUUID(), "Objetivo", null, completada);
    }

    @Test
    @DisplayName("cuenta hábitos y objetivos del día, y de los hábitos solo los que cuentan")
    void cuentaHabitosYObjetivos() {
        List<HabitoDelDiaResumen> habitos = List.of(habito("COMPLETADO", true), habito("PENDIENTE", true),
                habito("PENDIENTE", false), habito("COMPLETADO", true));
        List<RocaDelDiaResumen> objetivos = List.of(objetivo(true), objetivo(false), objetivo(false));

        CumplimientoDelDia hoy = CumplimientoDeHoy.de(HOY, habitos, objetivos);

        assertThat(hoy.habitosProgramados()).as("el opcional sin cumplir no cuenta").isEqualTo(3);
        assertThat(hoy.habitosCumplidos()).isEqualTo(2);
        assertThat(hoy.objetivosProgramados()).isEqualTo(3);
        assertThat(hoy.objetivosCumplidos()).isEqualTo(1);
        assertThat(hoy.fecha()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("un hábito opcional ya cumplido cuenta, porque así lo dice el resumen que manda habits")
    void unOpcionalCumplidoCuenta() {
        CumplimientoDelDia hoy = CumplimientoDeHoy.de(HOY, List.of(habito("COMPLETADO", true)), List.of());

        assertThat(hoy.habitosProgramados()).isEqualTo(1);
        assertThat(hoy.habitosCumplidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("si la tarjeta de hábitos no aplica a la persona (null), solo cuentan sus objetivos")
    void sinTarjetaDeHabitos() {
        CumplimientoDelDia hoy = CumplimientoDeHoy.de(HOY, null, List.of(objetivo(false), objetivo(true)));

        assertThat(hoy.habitosProgramados()).isZero();
        assertThat(hoy.objetivosProgramados()).isEqualTo(2);
        assertThat(hoy.objetivosCumplidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("sin nada programado el día queda sin datos, igual que lo calcula el semáforo en vivo")
    void sinNadaProgramado() {
        CumplimientoDelDia hoy = CumplimientoDeHoy.de(HOY, List.of(habito("PENDIENTE", false)), List.of());

        assertThat(hoy.aDia().estado()).isEqualTo(EstadoDiaSemaforo.SIN_DATOS);
    }

    @Test
    @DisplayName("un track cumplido que no cuenta no rompe la regla de cumplidos <= programados")
    void unCumplidoQueNoCuentaNoRompeElRango() {
        CumplimientoDelDia hoy = CumplimientoDeHoy.de(HOY, List.of(habito("COMPLETADO", false)), List.of());

        assertThat(hoy.habitosCumplidos()).isLessThanOrEqualTo(hoy.habitosProgramados());
    }
}

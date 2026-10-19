package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.HabitoDelDiaResumen;
import com.renaser.os.points.api.RocaDelDiaResumen;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;

import java.time.LocalDate;
import java.util.List;

/**
 * Lo que la persona tenía por hacer hoy y lo que ya hizo, armado con las listas que {@code /home} ya leyó para sus
 * tarjetas de hábitos y de objetivos (D-277). Así el {@code {faltan}} del mensaje no pide nada nuevo a la base: la
 * cuenta es la que hacía {@code SemaforoDelDiaFinder} (hábitos + objetivos, D-168) sobre los mismos registros.
 *
 * <p>Qué hábito cuenta lo dice {@code habits} en cada resumen ({@link HabitoDelDiaResumen#cuentaParaElDia()}: un
 * opcional sin completar no cuenta); un objetivo del día siempre cuenta. Este objeto no repite esa regla.
 */
public final class CumplimientoDeHoy {

    /** El estado del track tal como lo espeja {@link HabitoDelDiaResumen#estado()}. */
    static final String ESTADO_COMPLETADO = "COMPLETADO";

    private CumplimientoDeHoy() {
    }

    /**
     * @param hoy       el día local de la persona
     * @param habitos   los tracks de hoy; {@code null} si esa tarjeta no aplica a la persona (no tiene hábitos hoy)
     * @param objetivos las acciones planificadas para hoy
     */
    public static CumplimientoDelDia de(LocalDate hoy, List<HabitoDelDiaResumen> habitos,
                                        List<RocaDelDiaResumen> objetivos) {
        List<HabitoDelDiaResumen> queCuentan = habitos == null ? List.of()
                : habitos.stream().filter(HabitoDelDiaResumen::cuentaParaElDia).toList();
        int habitosCumplidos = (int) queCuentan.stream().filter(h -> ESTADO_COMPLETADO.equals(h.estado())).count();
        int objetivosCumplidos = (int) objetivos.stream().filter(RocaDelDiaResumen::completada).count();
        return new CumplimientoDelDia(hoy, queCuentan.size(), habitosCumplidos, objetivos.size(), objetivosCumplidos);
    }
}

package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.SemanaDelSemaforo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Qué le toca al barrido horario con una persona, decidido solo con fechas (regla 02 §2: derivar,
 * no incrementar). Correrlo dos veces da lo mismo y una corrida tarde se pone al día sola: si el
 * backend estuvo caído, la próxima corrida calcula los días que faltan y cierra las semanas que
 * quedaron abiertas.
 *
 * <p>Lo que se completa tarde cuenta para su día hasta el cierre del sábado (la app deja completar
 * días pasados); los días de una semana ya cerrada no se recalculan: lo reportado no cambia.
 */
public final class CierreSemanal {

    /** El aviso sale el sábado o el domingo; una semana cerrada más tarde queda en el historial sin avisar. */
    private static final int DIAS_PARA_AVISAR = 2;

    private CierreSemanal() {
    }

    /** Primer día que todavía puede cambiar: el siguiente a la última semana cerrada. */
    public static LocalDate primerDiaAbierto(LocalDate ultimaSemanaCerrada, CalendarioDeMedicion calendario) {
        LocalDate primeraFecha = calendario.primeraFecha();
        if (ultimaSemanaCerrada == null) {
            return primeraFecha;
        }
        LocalDate siguiente = ultimaSemanaCerrada.plusDays(1);
        return siguiente.isAfter(primeraFecha) ? siguiente : primeraFecha;
    }

    /** Último día ya cerrado que se puede medir: ayer, o el día 90 si ya pasó. */
    public static LocalDate ultimoDiaCerrado(LocalDate hoyLocal, CalendarioDeMedicion calendario) {
        LocalDate ayer = hoyLocal.minusDays(1);
        return ayer.isBefore(calendario.ultimaFecha()) ? ayer : calendario.ultimaFecha();
    }

    /**
     * Los viernes de las semanas que ya terminaron y todavía no tienen foto, de la más vieja a la
     * más nueva. Solo semanas que tocan el programa (la del día 1 y la del día 90 pueden ser parciales).
     */
    public static List<LocalDate> semanasPorCerrar(LocalDate hoyLocal, LocalDate ultimaSemanaCerrada,
                                                   CalendarioDeMedicion calendario) {
        LocalDate primera = SemanaDelSemaforo.cierreDe(calendario.primeraFecha());
        if (ultimaSemanaCerrada != null && !primera.isAfter(ultimaSemanaCerrada)) {
            primera = ultimaSemanaCerrada.plusWeeks(1);
        }
        LocalDate ultimaQueTermino = SemanaDelSemaforo.ultimaCerradaAl(hoyLocal);
        LocalDate ultimaDelPrograma = SemanaDelSemaforo.cierreDe(calendario.ultimaFecha());
        LocalDate tope = ultimaQueTermino.isBefore(ultimaDelPrograma) ? ultimaQueTermino : ultimaDelPrograma;
        List<LocalDate> viernes = new ArrayList<>();
        for (LocalDate cierre = primera; !cierre.isAfter(tope); cierre = cierre.plusWeeks(1)) {
            viernes.add(cierre);
        }
        return viernes;
    }

    /** El aviso del cierre solo sale dentro del fin de semana siguiente (sábado o domingo local). */
    public static boolean correspondeAvisar(LocalDate semanaHasta, LocalDate hoyLocal) {
        return !hoyLocal.isAfter(semanaHasta.plusDays(DIAS_PARA_AVISAR));
    }
}

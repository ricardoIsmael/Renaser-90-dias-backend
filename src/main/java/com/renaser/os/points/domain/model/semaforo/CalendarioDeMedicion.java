package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.EstadoDiaSemaforo;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Qué días de una persona se miden: los de su programa (día 1 al 90, fechas que calcula
 * {@code users}) que no están pausados ni cayeron con la cuenta suspendida. Es lo único que el
 * semáforo necesita saber de su programa.
 *
 * @param primeraFecha primera fecha local con día de programa ≥ 1
 * @param ultimaFecha  fecha local del día 90
 * @param pausas       los tramos sin medir: las pausas que pidió (solo el staff) y los días con la
 *                     cuenta suspendida (D-209)
 */
public record CalendarioDeMedicion(LocalDate primeraFecha, LocalDate ultimaFecha, List<PausaDeMedicion> pausas) {

    public CalendarioDeMedicion {
        Objects.requireNonNull(primeraFecha, "primeraFecha es obligatoria");
        Objects.requireNonNull(ultimaFecha, "ultimaFecha es obligatoria");
        pausas = pausas == null ? List.of() : List.copyOf(pausas);
    }

    public boolean dentroDelPrograma(LocalDate fecha) {
        return !fecha.isBefore(primeraFecha) && !fecha.isAfter(ultimaFecha);
    }

    /** Lo cubre una pausa que pidió la persona. */
    public boolean pausado(LocalDate fecha) {
        return cubiertoPor(MotivoDePausa.PEDIDA_POR_LA_PERSONA, fecha);
    }

    /** La cuenta estuvo suspendida ese día, aunque sea un rato (D-209). */
    public boolean conCuentaSuspendida(LocalDate fecha) {
        return cubiertoPor(MotivoDePausa.CUENTA_SUSPENDIDA, fecha);
    }

    public boolean seMide(LocalDate fecha) {
        return dentroDelPrograma(fecha) && pausas.stream().noneMatch(p -> p.cubre(fecha));
    }

    /**
     * El estado de un día que no tiene fila calculada: por qué no tiene porcentaje. Si una pausa del
     * staff y una suspensión se pisan, manda la suspensión: es lo que de verdad le pasó a la cuenta.
     */
    public EstadoDiaSemaforo estadoSinCalculo(LocalDate fecha) {
        if (!dentroDelPrograma(fecha)) {
            return EstadoDiaSemaforo.FUERA_DEL_PROGRAMA;
        }
        if (conCuentaSuspendida(fecha)) {
            return EstadoDiaSemaforo.CUENTA_SUSPENDIDA;
        }
        return pausado(fecha) ? EstadoDiaSemaforo.PAUSADO : EstadoDiaSemaforo.PENDIENTE;
    }

    private boolean cubiertoPor(MotivoDePausa motivo, LocalDate fecha) {
        return pausas.stream().anyMatch(p -> p.motivo() == motivo && p.cubre(fecha));
    }
}

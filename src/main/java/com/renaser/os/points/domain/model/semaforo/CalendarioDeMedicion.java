package com.renaser.os.points.domain.model.semaforo;

import com.renaser.os.points.api.EstadoDiaSemaforo;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Qué días de una persona se miden: los de su programa (día 1 al 90, fechas que calcula
 * {@code users}) que no están pausados. Es lo único que el semáforo necesita saber de su programa.
 *
 * @param primeraFecha primera fecha local con día de programa ≥ 1
 * @param ultimaFecha  fecha local del día 90
 * @param pausas       sus pausas (solo el staff las tiene)
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

    public boolean pausado(LocalDate fecha) {
        return pausas.stream().anyMatch(p -> p.cubre(fecha));
    }

    public boolean seMide(LocalDate fecha) {
        return dentroDelPrograma(fecha) && !pausado(fecha);
    }

    /** El estado de un día que no tiene fila calculada: por qué no tiene porcentaje. */
    public EstadoDiaSemaforo estadoSinCalculo(LocalDate fecha) {
        if (!dentroDelPrograma(fecha)) {
            return EstadoDiaSemaforo.FUERA_DEL_PROGRAMA;
        }
        return pausado(fecha) ? EstadoDiaSemaforo.PAUSADO : EstadoDiaSemaforo.PENDIENTE;
    }
}

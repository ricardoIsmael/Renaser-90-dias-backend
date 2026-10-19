package com.renaser.os.points.domain.model.semaforo.mensaje;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;

import java.util.List;
import java.util.Objects;

/**
 * En qué situación está la persona hoy, lo justo para saber a qué grupo del catálogo pertenece su mensaje.
 *
 * @param color            el color de la ventana vigente que muestra la tarjeta
 * @param pausado          hoy rige una pausa que pidió la persona
 * @param programaTerminado ya pasó su día 90
 * @param diaPrograma      día del programa en que está (0 antes del Día 1)
 * @param ventana          los 7 días de la ventana vigente
 */
public record SituacionDelSemaforo(ColorSemaforo color, boolean pausado, boolean programaTerminado, int diaPrograma,
                                   List<DiaDelSemaforo> ventana) {

    public SituacionDelSemaforo {
        Objects.requireNonNull(color, "color es obligatorio");
        ventana = ventana == null ? List.of() : List.copyOf(ventana);
    }
}

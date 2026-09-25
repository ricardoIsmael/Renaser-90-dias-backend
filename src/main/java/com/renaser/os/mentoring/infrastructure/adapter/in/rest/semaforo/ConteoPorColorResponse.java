package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;

/**
 * {@code {"verde": 5, "amarillo": 2, "rojo": 1, "sinDatos": 0, "total": 8}}: el {@code resumen} de
 * la tabla de un grupo y el {@code resumen}/{@code totales} del resumen por grupos
 * (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.3 y §4.4).
 */
public record ConteoPorColorResponse(int verde, int amarillo, int rojo, int sinDatos, int total) {

    static ConteoPorColorResponse from(ConteoPorColor conteo) {
        return new ConteoPorColorResponse(conteo.verde(), conteo.amarillo(), conteo.rojo(), conteo.sinDatos(),
                conteo.total());
    }
}

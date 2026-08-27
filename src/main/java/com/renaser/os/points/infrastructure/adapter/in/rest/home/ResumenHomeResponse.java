package com.renaser.os.points.infrastructure.adapter.in.rest.home;

import com.renaser.os.points.application.ports.in.home.ConsultarResumenHomeUseCase.ResumenHome;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /api/v1/home}. {@code bloqueos} documenta, en la propia respuesta, que datos
 * de otros modulos no se pudieron componer todavia y por que — ver javadoc de
 * {@code ConsultarResumenHomeUseCase}.
 */
public record ResumenHomeResponse(int puntosLiga, BigDecimal coherencia, int rachaActual, int rachaMaxima,
                                   List<String> bloqueos) {

    public static ResumenHomeResponse from(ResumenHome resumen) {
        return new ResumenHomeResponse(resumen.puntosLiga(), resumen.coherencia(), resumen.rachaActual(),
                resumen.rachaMaxima(), resumen.bloqueos());
    }
}

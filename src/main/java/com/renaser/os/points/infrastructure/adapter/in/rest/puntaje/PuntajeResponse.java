package com.renaser.os.points.infrastructure.adapter.in.rest.puntaje;

import com.renaser.os.points.application.ports.in.puntaje.ConsultarPuntajeVisibleUseCase.PuntajeVisible;

import java.math.BigDecimal;

public record PuntajeResponse(String participanteId, BigDecimal coherencia, int puntosLiga, int rachaActual,
                               int rachaMaxima) {

    /** E-216: la racha es la derivada (la de Hoy), no la columna guardada que siempre valia 0. */
    public static PuntajeResponse from(PuntajeVisible puntaje) {
        return new PuntajeResponse(puntaje.participanteId().toString(), puntaje.coherencia(), puntaje.puntosLiga(),
                puntaje.rachaActual(), puntaje.rachaMaxima());
    }
}

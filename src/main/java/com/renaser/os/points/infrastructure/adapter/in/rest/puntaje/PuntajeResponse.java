package com.renaser.os.points.infrastructure.adapter.in.rest.puntaje;

import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;

import java.math.BigDecimal;

public record PuntajeResponse(String participanteId, BigDecimal coherencia, int puntosLiga, int rachaActual,
                               int rachaMaxima) {

    public static PuntajeResponse from(PuntajeParticipante puntaje) {
        return new PuntajeResponse(puntaje.participanteId().toString(), puntaje.coherencia(), puntaje.puntosLiga(),
                puntaje.rachaActual(), puntaje.rachaMaxima());
    }
}

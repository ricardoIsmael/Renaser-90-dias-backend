package com.renaser.os.points.application.ports.out.puntaje;

import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;

public interface SavePuntajePort {

    PuntajeParticipante save(PuntajeParticipante puntaje);
}

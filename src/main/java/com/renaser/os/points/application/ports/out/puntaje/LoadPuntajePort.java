package com.renaser.os.points.application.ports.out.puntaje;

import com.renaser.os.points.domain.model.puntaje.PuntajeParticipante;
import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadPuntajePort {

    /** Lectura pura: no bloquea. */
    Optional<PuntajeParticipante> byParticipanteId(UserId participanteId);

    /**
     * Carga para modificar el saldo. Serializa a los que ajusten el mismo participante:
     * sin esto, dos ajustes concurrentes leen el mismo saldo de partida y el segundo
     * pisa al primero, dejando el saldo distinto de la suma de su libro mayor.
     */
    Optional<PuntajeParticipante> byParticipanteIdParaEscritura(UserId participanteId);
}

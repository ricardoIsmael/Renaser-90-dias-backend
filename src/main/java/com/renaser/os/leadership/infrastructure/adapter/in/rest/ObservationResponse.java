package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una observación del líder sobre un mentor (V89).
 *
 * @param personId   spec 005: la persona de la que trata; null si no trata de nadie en particular
 * @param personName su nombre; solo lo trae el listado de observaciones, y es null si la cuenta ya no existe
 * @param weekStart  el primer día de la semana que el líder miraba; null si no viene de una ficha
 */
public record ObservationResponse(UUID id, UUID mentorId, UUID authorId, String type, String text,
                                  boolean sentByChat, UUID messageId, Instant createdAt, UUID personId,
                                  String personName, LocalDate weekStart) {

    static ObservationResponse from(ObservacionDeMentor o) {
        return from(o, null);
    }

    static ObservationResponse from(ObservacionDeMentor o, String nombreDeLaPersona) {
        return new ObservationResponse(o.id(), o.mentorId().value(), o.autorId().value(), o.tipo().name(), o.texto(),
                o.enviadaPorChat(), o.mensajeId(), o.creadoEn(), o.personaId() == null ? null : o.personaId().value(),
                nombreDeLaPersona, o.semanaDesde());
    }
}

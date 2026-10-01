package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;

import java.time.Instant;
import java.util.UUID;

/** Una observación del líder sobre un mentor (V89). */
public record ObservationResponse(UUID id, UUID mentorId, UUID authorId, String type, String text,
                                  boolean sentByChat, UUID messageId, Instant createdAt) {

    static ObservationResponse from(ObservacionDeMentor o) {
        return new ObservationResponse(o.id(), o.mentorId().value(), o.autorId().value(), o.tipo().name(), o.texto(),
                o.enviadaPorChat(), o.mensajeId(), o.creadoEn());
    }
}

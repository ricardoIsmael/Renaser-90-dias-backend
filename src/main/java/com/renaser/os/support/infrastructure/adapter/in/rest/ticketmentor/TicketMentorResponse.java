package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import com.renaser.os.support.domain.model.ticketmentor.EstadoTicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;

import java.time.Instant;

public record TicketMentorResponse(
        String id,
        String traineeProfileId,
        String blockDescription,
        String attemptedSolutions,
        String smartGoalImpact,
        String status,
        String mentorAnswer,
        Instant answeredAt,
        boolean savedToLibrary,
        Instant createdAt) {

    public static TicketMentorResponse from(TicketMentor ticket) {
        return new TicketMentorResponse(
                ticket.id().value().toString(),
                ticket.participanteId().value().toString(),
                ticket.descripcionBloqueo(),
                ticket.solucionesIntentadas(),
                ticket.impactoMetaSmart(),
                toWireStatus(ticket.estado()),
                ticket.respuestaMentor(),
                ticket.respondidoEn(),
                ticket.guardadoEnBiblioteca(),
                ticket.creadoEn());
    }

    private static String toWireStatus(EstadoTicketMentor estado) {
        return switch (estado) {
            case ABIERTO -> "OPEN";
            case RESPONDIDO -> "ANSWERED";
        };
    }
}

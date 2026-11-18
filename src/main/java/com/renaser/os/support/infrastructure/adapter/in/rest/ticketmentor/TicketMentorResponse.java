package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import com.renaser.os.support.application.ports.in.ticketmentor.TicketConAutor;
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
        Instant createdAt,
        RespondidoPor respondidoPor) {

    /**
     * Quién respondió la consulta (spec 056, FR-030): el mentor o el líder de mentores. {@code null} mientras está abierta y en las respondidas antes de V88,
     * que no guardaron quién fue. {@code nombre} es {@code null} si la cuenta de quien respondió ya no existe.
     */
    public record RespondidoPor(String id, String nombre) {
    }

    /** Sin el nombre de quien respondió: solo para un ticket recién abierto, que todavía no lo tiene. */
    public static TicketMentorResponse from(TicketMentor ticket) {
        return from(ticket, null);
    }

    public static TicketMentorResponse from(TicketConAutor conAutor) {
        return from(conAutor.ticket(), conAutor.nombreDeQuienRespondio());
    }

    public static TicketMentorResponse from(TicketMentor ticket, String nombreDeQuienRespondio) {
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
                ticket.creadoEn(),
                ticket.respondidoPor() == null ? null : new RespondidoPor(ticket.respondidoPor().value().toString(), nombreDeQuienRespondio));
    }

    private static String toWireStatus(EstadoTicketMentor estado) {
        return switch (estado) {
            case ABIERTO -> "OPEN";
            case RESPONDIDO -> "ANSWERED";
        };
    }
}

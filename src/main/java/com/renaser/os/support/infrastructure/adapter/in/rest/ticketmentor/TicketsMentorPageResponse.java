package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase.TicketsMentorPage;

import java.util.List;

public record TicketsMentorPageResponse(List<TicketMentorResponse> tickets, String nextCursor) {

    public static TicketsMentorPageResponse from(TicketsMentorPage page) {
        List<TicketMentorResponse> tickets = page.tickets().stream().map(TicketMentorResponse::from).toList();
        String nextCursor = page.nextCursor() != null ? page.nextCursor().toString() : null;
        return new TicketsMentorPageResponse(tickets, nextCursor);
    }
}

package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/tickets")
public class TicketMentorAdminController {

    private final ListarTicketsMentorUseCase listarUseCase;

    public TicketMentorAdminController(ListarTicketsMentorUseCase listarUseCase) {
        this.listarUseCase = listarUseCase;
    }

    @GetMapping
    public TicketsMentorPageResponse todos(@ActorAutenticado UserId actor,
                                            @RequestParam(required = false) String cursor) {
        return TicketsMentorPageResponse.from(listarUseCase.todos(actor, parseCursor(cursor)));
    }

    /** cursor llega como ISO-8601 (mismo formato que el TicketsPageQuery viejo). */
    private static Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("cursor invalido, se espera ISO-8601: " + cursor);
        }
    }
}

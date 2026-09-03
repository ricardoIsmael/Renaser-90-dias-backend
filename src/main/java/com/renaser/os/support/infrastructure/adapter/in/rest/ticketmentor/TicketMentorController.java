package com.renaser.os.support.infrastructure.adapter.in.rest.ticketmentor;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase.AbrirTicketMentorCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.BuscarBibliotecaUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.BuscarBibliotecaUseCase.BuscarBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.GuardarEnBibliotecaUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.GuardarEnBibliotecaUseCase.GuardarEnBibliotecaCommand;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase.ResponderTicketMentorCommand;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketMentorController {

    private final AbrirTicketMentorUseCase abrirUseCase;
    private final ResponderTicketMentorUseCase responderUseCase;
    private final GuardarEnBibliotecaUseCase guardarUseCase;
    private final BuscarBibliotecaUseCase buscarUseCase;
    private final ListarTicketsMentorUseCase listarUseCase;

    public TicketMentorController(AbrirTicketMentorUseCase abrirUseCase, ResponderTicketMentorUseCase responderUseCase,
                                   GuardarEnBibliotecaUseCase guardarUseCase, BuscarBibliotecaUseCase buscarUseCase,
                                   ListarTicketsMentorUseCase listarUseCase) {
        this.abrirUseCase = abrirUseCase;
        this.responderUseCase = responderUseCase;
        this.guardarUseCase = guardarUseCase;
        this.buscarUseCase = buscarUseCase;
        this.listarUseCase = listarUseCase;
    }

    @RequiresPermission(Permission.OPEN_MENTOR_TICKET)
    @PostMapping
    public ResponseEntity<TicketMentorResponse> abrir(@ActorAutenticado UserId actor,
                                                        @RequestBody @Valid AbrirTicketMentorRequest request) {
        TicketMentor ticket = abrirUseCase.abrir(new AbrirTicketMentorCommand(actor,
                request.blockDescription(), request.attemptedSolutions(), request.smartGoalImpact()));
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketMentorResponse.from(ticket));
    }

    @RequiresPermission(value = Permission.USE_MENTOR_TICKETS, scope = "un TRAINEE ve los suyos; hoy un MENTOR ve todos y no solo los de sus aprendices (deuda ya anotada en TicketMentorService)")
    @GetMapping
    public TicketsMentorPageResponse propios(@ActorAutenticado UserId actor,
                                              @RequestParam(required = false) String cursor) {
        return TicketsMentorPageResponse.from(listarUseCase.propios(actor, parseCursor(cursor)));
    }

    @RequiresPermission(value = Permission.ANSWER_MENTOR_TICKET, scope = "solo el mentor asignado a ese aprendiz")
    @PostMapping("/{id}/answer")
    public TicketMentorResponse responder(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                           @RequestBody @Valid ResponderTicketMentorRequest request) {
        TicketMentor ticket = responderUseCase.responder(new ResponderTicketMentorCommand(TicketMentorId.of(id),
                actor, request.mentorAnswer()));
        return TicketMentorResponse.from(ticket);
    }

    @RequiresPermission(value = Permission.ANSWER_MENTOR_TICKET, scope = "solo el mentor asignado a ese aprendiz")
    @PostMapping("/{id}/save-to-library")
    public TicketMentorResponse guardarEnBiblioteca(@PathVariable UUID id,
                                                      @ActorAutenticado UserId actor) {
        TicketMentor ticket = guardarUseCase.guardar(new GuardarEnBibliotecaCommand(TicketMentorId.of(id),
                actor));
        return TicketMentorResponse.from(ticket);
    }

    @RequiresPermission(Permission.USE_MENTOR_TICKETS)
    @GetMapping("/library")
    public BibliotecaSearchResponse buscarEnBiblioteca(@ActorAutenticado UserId actor,
                                                         @RequestParam String q) {
        return new BibliotecaSearchResponse(buscarUseCase.buscar(new BuscarBibliotecaCommand(actor, q)));
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

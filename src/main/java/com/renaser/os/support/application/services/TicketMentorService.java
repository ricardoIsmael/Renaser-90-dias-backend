package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.TicketMentorAbiertoEvent;
import com.renaser.os.support.api.TicketMentorRespondidoEvent;
import com.renaser.os.support.application.ports.in.ticketmentor.AbrirTicketMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.BuscarBibliotecaUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.GuardarEnBibliotecaUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.ListarTicketsMentorUseCase;
import com.renaser.os.support.application.ports.in.ticketmentor.ResponderTicketMentorUseCase;
import com.renaser.os.support.application.ports.out.ticketmentor.BuscarBibliotecaPort;
import com.renaser.os.support.application.ports.out.ticketmentor.LoadTicketMentorPort;
import com.renaser.os.support.application.ports.out.ticketmentor.SaveTicketMentorPort;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TicketMentorService implements AbrirTicketMentorUseCase, ResponderTicketMentorUseCase,
        GuardarEnBibliotecaUseCase, BuscarBibliotecaUseCase, ListarTicketsMentorUseCase {

    private static final int PAGE_SIZE = 30;
    private static final int LIBRARY_SEARCH_COUNT = 5;

    private final LoadTicketMentorPort loadTicketMentorPort;
    private final SaveTicketMentorPort saveTicketMentorPort;
    private final BuscarBibliotecaPort buscarBibliotecaPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ParticipacionProgramaFinder participacionFinder;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TicketMentorService(LoadTicketMentorPort loadTicketMentorPort, SaveTicketMentorPort saveTicketMentorPort,
                                BuscarBibliotecaPort buscarBibliotecaPort, UserSummaryFinder userSummaryFinder,
                                ParticipacionProgramaFinder participacionFinder,
                                ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator) {
        this.loadTicketMentorPort = loadTicketMentorPort;
        this.saveTicketMentorPort = saveTicketMentorPort;
        this.buscarBibliotecaPort = buscarBibliotecaPort;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionFinder = participacionFinder;
        this.events = events;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public TicketMentor abrir(AbrirTicketMentorCommand command) {
        requireRol(command.participanteId(), UserRole.TRAINEE, "Solo un aprendiz puede abrir un ticket");
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD §5.4.7).
        TicketMentor ticket = TicketMentor.abrir(TicketMentorId.of(idGenerator.newId()),
                command.participanteId(), command.descripcionBloqueo(), command.solucionesIntentadas(),
                command.impactoMetaSmart(), clock);
        TicketMentor saved = saveTicketMentorPort.save(ticket);
        events.publishEvent(new TicketMentorAbiertoEvent(saved.id(), saved.participanteId(), clock.now()));
        return saved;
    }

    @Override
    @Transactional
    public TicketMentor responder(ResponderTicketMentorCommand command) {
        requireRol(command.actorId(), UserRole.MENTOR, "Solo el mentor asignado puede responder un ticket");
        TicketMentor ticket = requireTicket(command.ticketId());
        requireMentorAsignado(command.actorId(), ticket);
        ticket.responder(command.respuesta(), clock);
        TicketMentor saved = saveTicketMentorPort.save(ticket);
        events.publishEvent(new TicketMentorRespondidoEvent(saved.id(), saved.participanteId(), clock.now()));
        return saved;
    }

    @Override
    @Transactional
    public TicketMentor guardar(GuardarEnBibliotecaCommand command) {
        requireRol(command.actorId(), UserRole.MENTOR, "Solo el mentor asignado puede guardar en la biblioteca");
        TicketMentor ticket = requireTicket(command.ticketId());
        requireMentorAsignado(command.actorId(), ticket);
        ticket.guardarEnBiblioteca();
        return saveTicketMentorPort.save(ticket);
    }

    @Override
    public List<String> buscar(BuscarBibliotecaCommand command) {
        UserSummary actor = requireActor(command.actorId());
        if (actor.role() != UserRole.TRAINEE && actor.role() != UserRole.MENTOR) {
            throw new NotAuthorizedException("Solo un aprendiz o un mentor pueden buscar en la biblioteca");
        }
        return buscarBibliotecaPort.buscar(command.query(), LIBRARY_SEARCH_COUNT).stream()
                .map(entrada -> "Pregunta: " + entrada.descripcionBloqueo() + "\nRespuesta: "
                        + entrada.respuestaMentor())
                .toList();
    }

    /** TRAINEE: los suyos. MENTOR: todos (deuda de celula, ver javadoc de la interfaz). Otro rol -> 403. */
    @Override
    public TicketsMentorPage propios(UserId actorId, Instant cursor) {
        UserSummary actor = requireActor(actorId);
        if (actor.role() == UserRole.TRAINEE) {
            return paginar(loadTicketMentorPort.porParticipante(actorId, cursor, PAGE_SIZE + 1));
        }
        if (actor.role() == UserRole.MENTOR) {
            return paginar(loadTicketMentorPort.todos(cursor, PAGE_SIZE + 1));
        }
        throw new NotAuthorizedException("Solo un aprendiz o un mentor pueden listar estos tickets");
    }

    /** MENTOR_LEAD/ADMIN/ALCHEMIST unicamente (vista de plataforma, solo lectura). */
    @Override
    public TicketsMentorPage todos(UserId actorId, Instant cursor) {
        UserSummary actor = requireActor(actorId);
        if (actor.role() != UserRole.MENTOR_LEAD && actor.role() != UserRole.ADMIN
                && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo MENTOR_LEAD/ADMIN/ALCHEMIST ven todos los tickets");
        }
        return paginar(loadTicketMentorPort.todos(cursor, PAGE_SIZE + 1));
    }

    private TicketsMentorPage paginar(List<TicketMentor> rows) {
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<TicketMentor> page = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
        Instant nextCursor = hasMore ? page.get(page.size() - 1).creadoEn() : null;
        return new TicketsMentorPage(page, nextCursor);
    }

    private void requireRol(UserId actorId, UserRole requerido, String mensaje) {
        UserSummary actor = requireActor(actorId);
        if (actor.role() != requerido) {
            throw new NotAuthorizedException(mensaje);
        }
    }

    /**
     * El rol MENTOR no alcanza: tiene que ser EL mentor asignado a ese aprendiz. Antes
     * faltaba esta verificacion y cualquier mentor podia responder o archivar el ticket de
     * un aprendiz ajeno — el mensaje de error decia "solo el mentor asignado" pero el codigo
     * solo miraba el rol (E-38, docs/BITACORA_ERRORES.md).
     */
    private void requireMentorAsignado(UserId actorId, TicketMentor ticket) {
        UserId mentorAsignado = participacionFinder.deParticipante(ticket.participanteId())
                .map(ParticipacionPrograma::mentorId)
                .orElse(null);
        if (!actorId.equals(mentorAsignado)) {
            throw new NotAuthorizedException("Solo el mentor asignado a ese aprendiz puede operar su ticket");
        }
    }

    private UserSummary requireActor(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        return actor;
    }

    private TicketMentor requireTicket(TicketMentorId id) {
        return loadTicketMentorPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Ticket no encontrado: " + id));
    }
}

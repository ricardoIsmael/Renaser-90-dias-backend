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
import com.renaser.os.support.application.ports.in.ticketmentor.TicketConAutor;
import com.renaser.os.support.application.ports.out.ticketmentor.AprendicesDelAcompanantePort;
import com.renaser.os.support.application.ports.out.ticketmentor.BuscarBibliotecaPort;
import com.renaser.os.support.application.ports.out.ticketmentor.LoadTicketMentorPort;
import com.renaser.os.support.application.ports.out.ticketmentor.SaveTicketMentorPort;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

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
    private final AprendicesDelAcompanantePort aprendicesDelAcompanante;
    private final AlcanceDeFormacionFinder formaciones;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TicketMentorService(LoadTicketMentorPort loadTicketMentorPort, SaveTicketMentorPort saveTicketMentorPort,
                                BuscarBibliotecaPort buscarBibliotecaPort, UserSummaryFinder userSummaryFinder,
                                ParticipacionProgramaFinder participacionFinder,
                                AprendicesDelAcompanantePort aprendicesDelAcompanante, AlcanceDeFormacionFinder formaciones,
                                ApplicationEventPublisher events, Clock clock, IdGenerator idGenerator) {
        this.loadTicketMentorPort = loadTicketMentorPort;
        this.saveTicketMentorPort = saveTicketMentorPort;
        this.buscarBibliotecaPort = buscarBibliotecaPort;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionFinder = participacionFinder;
        this.aprendicesDelAcompanante = aprendicesDelAcompanante;
        this.formaciones = formaciones;
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
        events.publishEvent(new TicketMentorAbiertoEvent(saved.id().value(), saved.participanteId(), clock.now()));
        return saved;
    }

    /**
     * Responde el mentor asignado al aprendiz (E-38) o, desde el 18/11 (spec 056, FR-030; decisión del dueño), el líder de mentores de la formación del
     * aprendiz. El líder no tiene que ser «el asignado»: basta que el aprendiz sea de una de las formaciones que tiene activas, y fuera de ellas es 403
     * {@code FUERA_DE_SU_ALCANCE}. Quien respondió queda en el ticket ({@code respondido_por}, V88) y de ahí sale el nombre que ve el aprendiz.
     */
    @Override
    @Transactional
    public TicketConAutor responder(ResponderTicketMentorCommand command) {
        UserSummary actor = requireActor(command.actorId());
        TicketMentor ticket;
        if (actor.role() == UserRole.MENTOR_LEAD) {
            ticket = requireTicket(command.ticketId());
            exigirQueSeaDeSuFormacion(actor, ticket);
        } else {
            requireActuaComoMentor(command.actorId(), "Solo el mentor asignado puede responder un ticket");
            ticket = requireTicket(command.ticketId());
            requireMentorAsignado(command.actorId(), ticket);
        }
        ticket.responder(command.respuesta(), command.actorId(), clock);
        TicketMentor saved = saveTicketMentorPort.save(ticket);
        events.publishEvent(new TicketMentorRespondidoEvent(saved.id().value(), saved.participanteId(), clock.now()));
        return conAutor(saved);
    }

    /** El líder solo responde a aprendices de las formaciones que tiene activas (spec 056, FR-030); el 403 lleva su código para que la app lo reconozca. */
    private void exigirQueSeaDeSuFormacion(UserSummary lider, TicketMentor ticket) {
        AlcanceDeFormacion alcance = formaciones.de(lider.id());
        Set<Formacion> delAprendiz = formaciones.formacionesDe(ticket.participanteId());
        if (delAprendiz.stream().noneMatch(alcance::incluye)) {
            throw new NotAuthorizedException("Ese aprendiz es de otra formación: tú respondes solo las consultas de tus formaciones.",
                    NotAuthorizedException.FUERA_DE_SU_ALCANCE);
        }
    }

    private TicketConAutor conAutor(TicketMentor ticket) {
        return new TicketConAutor(ticket, nombresDeQuienRespondio(List.of(ticket)).get(ticket.respondidoPor()));
    }

    /** Una sola lectura de cuentas para todos los tickets, nunca una por ticket; quien no está (cuenta borrada) no aparece. */
    private Map<UserId, String> nombresDeQuienRespondio(Collection<TicketMentor> tickets) {
        List<UserId> autores = tickets.stream().map(TicketMentor::respondidoPor).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UserId, String> nombres = new HashMap<>();
        if (!autores.isEmpty()) {
            userSummaryFinder.findByIds(autores).forEach((id, perfil) -> nombres.put(id, perfil.fullName()));
        }
        return nombres;
    }

    @Override
    @Transactional
    public TicketConAutor guardar(GuardarEnBibliotecaCommand command) {
        requireActuaComoMentor(command.actorId(), "Solo el mentor asignado puede guardar en la biblioteca");
        TicketMentor ticket = requireTicket(command.ticketId());
        requireMentorAsignado(command.actorId(), ticket);
        ticket.guardarEnBiblioteca();
        return conAutor(saveTicketMentorPort.save(ticket));
    }

    @Override
    public List<String> buscar(BuscarBibliotecaCommand command) {
        UserSummary actor = requireActor(command.actorId());
        if (actor.role() != UserRole.TRAINEE && !actor.role().actuaComoMentor()) {
            throw new NotAuthorizedException("Solo un aprendiz o un mentor pueden buscar en la biblioteca");
        }
        return buscarBibliotecaPort.buscar(command.query(), LIBRARY_SEARCH_COUNT).stream()
                .map(entrada -> "Pregunta: " + entrada.descripcionBloqueo() + "\nRespuesta: "
                        + entrada.respuestaMentor())
                .toList();
    }

    /**
     * TRAINEE: los suyos. MENTOR y TRAINEE_GUIDE (D-265): los de los aprendices que acompañan hoy, la misma pregunta que responde
     * {@code /mentor/groups/{id}/learners}; quien no acompaña a nadie recibe una página vacía. Otro rol -> 403: ADMIN, ALCHEMIST y
     * MENTOR_LEAD ven todos por {@link #todos}.
     *
     * <blockquote><b>Corregido 2026-11-12 (D-350).</b> Esta línea decía «MENTOR: todos (deuda de célula)»: la deuda ya está saldada, un
     * mentor y una guía ven solo los tickets de sus aprendices.</blockquote>
     */
    @Override
    public TicketsMentorPage propios(UserId actorId, Instant cursor) {
        UserSummary actor = requireActor(actorId);
        if (actor.role() == UserRole.TRAINEE) {
            return paginar(loadTicketMentorPort.porParticipante(actorId, cursor, PAGE_SIZE + 1));
        }
        if (actor.role().actuaComoMentor()) {
            Set<UserId> aprendices = aprendicesDelAcompanante.aprendicesQueAcompana(actorId, clock.now());
            return paginar(loadTicketMentorPort.deParticipantes(aprendices, cursor, PAGE_SIZE + 1));
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
        return new TicketsMentorPage(page, nextCursor, nombresDeQuienRespondio(page));
    }

    private void requireRol(UserId actorId, UserRole requerido, String mensaje) {
        UserSummary actor = requireActor(actorId);
        if (actor.role() != requerido) {
            throw new NotAuthorizedException(mensaje);
        }
    }

    /** MENTOR o GUÍA DE APRENDIZ (D-265: el guía hace lo mismo que el mentor). */
    private void requireActuaComoMentor(UserId actorId, String mensaje) {
        UserSummary actor = requireActor(actorId);
        if (!actor.role().actuaComoMentor()) {
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

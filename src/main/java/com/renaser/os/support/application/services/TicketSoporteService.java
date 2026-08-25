package com.renaser.os.support.application.services;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.in.ticketsoporte.AbrirTicketSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.ListarTicketsSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.ResolverTicketSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.SolicitarUrlAdjuntoSoporteUseCase;
import com.renaser.os.support.application.ports.in.ticketsoporte.TicketSoporteVista;
import com.renaser.os.support.application.ports.out.ticketsoporte.LoadTicketSoportePort;
import com.renaser.os.support.application.ports.out.ticketsoporte.SaveTicketSoportePort;
import com.renaser.os.support.domain.model.ticketsoporte.AdjuntoSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.CategoriaSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TicketSoporteService implements AbrirTicketSoporteUseCase, ListarTicketsSoporteUseCase,
        ResolverTicketSoporteUseCase, SolicitarUrlAdjuntoSoporteUseCase {

    private static final String PREFIJO_RUTA = "soporte/";

    private static final String BUCKET_PLACEHOLDER = "renaser-files";

    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(30);

    private final LoadTicketSoportePort loadTicketSoportePort;
    private final SaveTicketSoportePort saveTicketSoportePort;
    private final UserSummaryFinder userSummaryFinder;
    private final AlmacenamientoPort almacenamientoPort;
    private final Clock clock;

    public TicketSoporteService(LoadTicketSoportePort loadTicketSoportePort,
                                 SaveTicketSoportePort saveTicketSoportePort, UserSummaryFinder userSummaryFinder,
                                 AlmacenamientoPort almacenamientoPort, Clock clock) {
        this.loadTicketSoportePort = loadTicketSoportePort;
        this.saveTicketSoportePort = saveTicketSoportePort;
        this.userSummaryFinder = userSummaryFinder;
        this.almacenamientoPort = almacenamientoPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TicketSoporteVista abrir(AbrirTicketSoporteCommand command) {
        CategoriaSoporte categoria = command.categoria() != null ? command.categoria() : CategoriaSoporte.OTRO;
        AdjuntoSoporte adjunto = command.adjuntoBucket() != null && command.adjuntoRuta() != null
                ? new AdjuntoSoporte(command.adjuntoBucket(), command.adjuntoRuta())
                : null;
        TicketSoporte ticket = TicketSoporte.abrir(command.usuarioId(), categoria, command.asunto(),
                command.mensaje(), command.clientLog(), adjunto, clock);
        return aVista(saveTicketSoportePort.save(ticket));
    }

    @Override
    public List<TicketSoporteVista> misTickets(UserId usuarioId) {
        return loadTicketSoportePort.porUsuario(usuarioId).stream().map(this::aVista).toList();
    }

    @Override
    public List<TicketSoporteVista> todos(UserId actorId, EstadoTicketSoporte estadoFiltro) {
        requireAdmin(actorId);
        return loadTicketSoportePort.todos(estadoFiltro).stream().map(this::aVista).toList();
    }

    @Override
    @Transactional
    public TicketSoporteVista resolver(ResolverTicketSoporteCommand command) {
        requireAdmin(command.actorId());
        TicketSoporte ticket = requireTicket(command.ticketId());
        ticket.resolver(command.adminNotes(), clock);
        return aVista(saveTicketSoportePort.save(ticket));
    }

    /** Firma la URL de lectura del adjunto (si tiene) — nunca se persiste, solo se firma al leer. */
    private TicketSoporteVista aVista(TicketSoporte ticket) {
        if (ticket.adjunto() == null) {
            return new TicketSoporteVista(ticket, null);
        }
        URI url = almacenamientoPort.firmarLectura(ticket.adjunto().ruta(), VALIDEZ_URL_LECTURA);
        return new TicketSoporteVista(ticket, url);
    }

    @Override
    public UrlAdjuntoSoporte solicitar(SolicitarUrlAdjuntoCommand command) {
        String ruta = PREFIJO_RUTA + command.usuarioId().value() + "/" + clock.now().toEpochMilli()
                + extraerExtension(command.nombreArchivo());
        URI urlSubida = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlAdjuntoSoporte(BUCKET_PLACEHOLDER, ruta, urlSubida);
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST administran tickets de soporte");
        }
    }

    private TicketSoporte requireTicket(TicketSoporteId id) {
        return loadTicketSoportePort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Ticket de soporte no encontrado: " + id));
    }

    private static String extraerExtension(String nombreArchivo) {
        int dot = nombreArchivo.lastIndexOf('.');
        return dot >= 0 ? nombreArchivo.substring(dot) : "";
    }
}

package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketmentor.BuscarBibliotecaPort;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketPendiente;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketRespondido;
import com.renaser.os.support.application.ports.out.ticketmentor.ConsultarAtencionDeTicketsPort;
import com.renaser.os.support.application.ports.out.ticketmentor.LoadTicketMentorPort;
import com.renaser.os.support.application.ports.out.ticketmentor.SaveTicketMentorPort;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Component
class TicketMentorPersistenceAdapter implements LoadTicketMentorPort, SaveTicketMentorPort, BuscarBibliotecaPort,
        ConsultarAtencionDeTicketsPort {

    private final SpringDataTicketMentorRepository repository;
    private final TicketMentorPersistenceMapper mapper;

    TicketMentorPersistenceAdapter(SpringDataTicketMentorRepository repository,
                                    TicketMentorPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TicketMentor> byId(TicketMentorId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<TicketMentor> porParticipante(UserId participanteId, Instant cursor, int limite) {
        List<TicketMentorJpaEntity> rows = cursor == null
                ? repository.findByParticipanteIdOrderByCreadoEnDesc(participanteId.value(), Limit.of(limite))
                : repository.findByParticipanteIdAndCreadoEnBeforeOrderByCreadoEnDesc(participanteId.value(), cursor,
                        Limit.of(limite));
        return rows.stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<TicketMentor> todos(Instant cursor, int limite) {
        List<TicketMentorJpaEntity> rows = cursor == null
                ? repository.findByOrderByCreadoEnDesc(Limit.of(limite))
                : repository.findByCreadoEnBeforeOrderByCreadoEnDesc(cursor, Limit.of(limite));
        return rows.stream().map(mapper::toDomain).toList();
    }

    @Override
    public TicketMentor save(TicketMentor ticket) {
        try {
            var saved = repository.saveAndFlush(mapper.toEntity(ticket));
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "No se pudo guardar el ticket: el participante " + ticket.participanteId()
                            + " no tiene inscripcion activa en el programa (participantes_programa)", e);
        }
    }

    @Override
    public List<EntradaBiblioteca> buscar(String query, int limite) {
        return repository.buscarEnBiblioteca(query, limite).stream()
                .map(row -> new EntradaBiblioteca(row.getDescripcionBloqueo(), row.getRespuestaMentor()))
                .toList();
    }

    @Override
    public List<TicketPendiente> pendientesDe(Collection<UserId> participantes) {
        return repository.findByEstadoAndParticipanteIdIn(EstadoTicketMentorJpa.ABIERTO, valores(participantes)).stream()
                .map(e -> new TicketPendiente(e.getId(), UserId.of(e.getParticipanteId()), e.getCreadoEn()))
                .toList();
    }

    @Override
    public List<TicketRespondido> respondidosPor(Collection<UserId> respondedores, Instant desde, Instant hasta) {
        return repository.findByRespondidoPorInAndRespondidoEnGreaterThanEqualAndRespondidoEnLessThan(
                        valores(respondedores), desde, hasta).stream()
                .map(e -> new TicketRespondido(e.getId(), UserId.of(e.getRespondidoPor()), e.getCreadoEn(),
                        e.getRespondidoEn()))
                .toList();
    }

    @Override
    public int respondidosSinAtribucion(Instant desde, Instant hasta) {
        return Math.toIntExact(repository.countByEstadoAndRespondidoPorIsNullAndRespondidoEnGreaterThanEqualAndRespondidoEnLessThan(
                EstadoTicketMentorJpa.RESPONDIDO, desde, hasta));
    }

    private static List<UUID> valores(Collection<UserId> ids) {
        return ids.stream().map(UserId::value).distinct().toList();
    }
}

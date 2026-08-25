package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketmentor.BuscarBibliotecaPort;
import com.renaser.os.support.application.ports.out.ticketmentor.LoadTicketMentorPort;
import com.renaser.os.support.application.ports.out.ticketmentor.SaveTicketMentorPort;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class TicketMentorPersistenceAdapter implements LoadTicketMentorPort, SaveTicketMentorPort, BuscarBibliotecaPort {

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
}

package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketsoporte;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketsoporte.LoadTicketSoportePort;
import com.renaser.os.support.application.ports.out.ticketsoporte.SaveTicketSoportePort;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class TicketSoportePersistenceAdapter implements LoadTicketSoportePort, SaveTicketSoportePort {

    private final SpringDataTicketSoporteRepository repository;
    private final TicketSoportePersistenceMapper mapper;

    TicketSoportePersistenceAdapter(SpringDataTicketSoporteRepository repository,
                                     TicketSoportePersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TicketSoporte> byId(TicketSoporteId id) {
        return repository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<TicketSoporte> porUsuario(UserId usuarioId) {
        return repository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId.value()).stream().map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<TicketSoporte> todos(EstadoTicketSoporte estadoFiltro) {
        List<TicketSoporteJpaEntity> rows = estadoFiltro == null
                ? repository.findByOrderByCreadoEnDesc()
                : repository.findByEstadoOrderByCreadoEnDesc(toJpaEstado(estadoFiltro));
        return rows.stream().map(mapper::toDomain).toList();
    }

    @Override
    public TicketSoporte save(TicketSoporte ticket) {
        var saved = repository.save(mapper.toEntity(ticket));
        return mapper.toDomain(saved);
    }

    private EstadoTicketSoporteJpa toJpaEstado(EstadoTicketSoporte estado) {
        return switch (estado) {
            case ABIERTO -> EstadoTicketSoporteJpa.ABIERTO;
            case RESUELTO -> EstadoTicketSoporteJpa.RESUELTO;
        };
    }
}

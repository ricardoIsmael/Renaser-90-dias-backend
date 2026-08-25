package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketsoporte.LoadTicketSoportePort;
import com.renaser.os.support.application.ports.out.ticketsoporte.SaveTicketSoportePort;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fake en memoria de LoadTicketSoportePort/SaveTicketSoportePort, para tests de servicio sin Postgres. */
class InMemoryTicketSoportePort implements LoadTicketSoportePort, SaveTicketSoportePort {

    private final Map<TicketSoporteId, TicketSoporte> store = new LinkedHashMap<>();

    @Override
    public Optional<TicketSoporte> byId(TicketSoporteId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<TicketSoporte> porUsuario(UserId usuarioId) {
        return store.values().stream()
                .filter(t -> t.usuarioId().equals(usuarioId))
                .sorted((a, b) -> b.creadoEn().compareTo(a.creadoEn()))
                .toList();
    }

    @Override
    public List<TicketSoporte> todos(EstadoTicketSoporte estadoFiltro) {
        return store.values().stream()
                .filter(t -> estadoFiltro == null || t.estado() == estadoFiltro)
                .sorted((a, b) -> b.creadoEn().compareTo(a.creadoEn()))
                .toList();
    }

    @Override
    public TicketSoporte save(TicketSoporte ticket) {
        store.put(ticket.id(), ticket);
        return ticket;
    }
}

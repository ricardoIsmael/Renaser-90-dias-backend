package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.application.ports.out.ticketmentor.BuscarBibliotecaPort;
import com.renaser.os.support.application.ports.out.ticketmentor.LoadTicketMentorPort;
import com.renaser.os.support.application.ports.out.ticketmentor.SaveTicketMentorPort;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentor;
import com.renaser.os.support.domain.model.ticketmentor.TicketMentorId;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Fake en memoria de los tres puertos out de TicketMentor, para tests de servicio sin Postgres. */
class InMemoryTicketMentorPort implements LoadTicketMentorPort, SaveTicketMentorPort, BuscarBibliotecaPort {

    private final Map<TicketMentorId, TicketMentor> store = new LinkedHashMap<>();

    @Override
    public Optional<TicketMentor> byId(TicketMentorId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<TicketMentor> porParticipante(UserId participanteId, Instant cursor, int limite) {
        return store.values().stream()
                .filter(t -> t.participanteId().equals(participanteId))
                .filter(t -> cursor == null || t.creadoEn().isBefore(cursor))
                .sorted((a, b) -> b.creadoEn().compareTo(a.creadoEn()))
                .limit(limite)
                .toList();
    }

    @Override
    public List<TicketMentor> todos(Instant cursor, int limite) {
        return store.values().stream()
                .filter(t -> cursor == null || t.creadoEn().isBefore(cursor))
                .sorted((a, b) -> b.creadoEn().compareTo(a.creadoEn()))
                .limit(limite)
                .toList();
    }

    @Override
    public TicketMentor save(TicketMentor ticket) {
        store.put(ticket.id(), ticket);
        return ticket;
    }

    @Override
    public List<EntradaBiblioteca> buscar(String query, int limite) {
        return store.values().stream()
                .filter(TicketMentor::guardadoEnBiblioteca)
                .map(t -> new EntradaBiblioteca(t.descripcionBloqueo(), t.respuestaMentor()))
                .limit(limite)
                .toList();
    }
}

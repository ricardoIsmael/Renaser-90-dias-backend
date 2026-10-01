package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder;
import com.renaser.os.support.application.ports.out.ticketmentor.ConsultarAtencionDeTicketsPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Implementacion de {@link AtencionDeTicketsFinder}. Solo lectura y sin autorizacion propia: quien la
 * consume (la gestion del Lider de Mentores) ya decidio si el actor puede mirar.
 */
@Service
public class AtencionDeTicketsFinderService implements AtencionDeTicketsFinder {

    private final ConsultarAtencionDeTicketsPort port;

    public AtencionDeTicketsFinderService(ConsultarAtencionDeTicketsPort port) {
        this.port = port;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketPendiente> pendientesDe(Collection<UserId> participantes) {
        return participantes == null || participantes.isEmpty() ? List.of() : port.pendientesDe(participantes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketRespondido> respondidosPor(Collection<UserId> respondedores, Instant desde, Instant hasta) {
        if (respondedores == null || respondedores.isEmpty() || !hasta.isAfter(desde)) {
            return List.of();
        }
        return port.respondidosPor(respondedores, desde, hasta);
    }

    @Override
    @Transactional(readOnly = true)
    public int respondidosSinAtribucion(Instant desde, Instant hasta) {
        return hasta.isAfter(desde) ? port.respondidosSinAtribucion(desde, hasta) : 0;
    }
}

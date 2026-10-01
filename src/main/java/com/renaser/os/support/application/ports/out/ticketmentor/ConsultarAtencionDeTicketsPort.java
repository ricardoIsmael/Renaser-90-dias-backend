package com.renaser.os.support.application.ports.out.ticketmentor;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketPendiente;
import com.renaser.os.support.api.AtencionDeTicketsFinder.TicketRespondido;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Lecturas en lote para medir la atencion de los mentores (D-241). Ver {@code AtencionDeTicketsFinder}. */
public interface ConsultarAtencionDeTicketsPort {

    List<TicketPendiente> pendientesDe(Collection<UserId> participantes);

    List<TicketRespondido> respondidosPor(Collection<UserId> respondedores, Instant desde, Instant hasta);

    int respondidosSinAtribucion(Instant desde, Instant hasta);
}

package com.renaser.os.support.application.ports.in.ticketsoporte;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;

import java.util.List;

public interface ListarTicketsSoporteUseCase {

    List<TicketSoporteVista> misTickets(UserId usuarioId);

    /** estadoFiltro null = sin filtrar. Lanza NotAuthorizedException si el actor no es ADMIN/ALCHEMIST. */
    List<TicketSoporteVista> todos(UserId actorId, EstadoTicketSoporte estadoFiltro);
}

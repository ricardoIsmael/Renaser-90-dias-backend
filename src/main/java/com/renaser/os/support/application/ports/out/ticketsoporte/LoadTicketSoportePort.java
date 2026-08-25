package com.renaser.os.support.application.ports.out.ticketsoporte;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.support.domain.model.ticketsoporte.EstadoTicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;
import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporteId;

import java.util.List;
import java.util.Optional;

public interface LoadTicketSoportePort {

    Optional<TicketSoporte> byId(TicketSoporteId id);

    /** Ordenado por creadoEn desc. */
    List<TicketSoporte> porUsuario(UserId usuarioId);

    List<TicketSoporte> todos(EstadoTicketSoporte estadoFiltro);
}

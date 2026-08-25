package com.renaser.os.support.application.ports.out.ticketsoporte;

import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;

public interface SaveTicketSoportePort {

    TicketSoporte save(TicketSoporte ticket);
}

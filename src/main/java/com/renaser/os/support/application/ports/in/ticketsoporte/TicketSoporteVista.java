package com.renaser.os.support.application.ports.in.ticketsoporte;

import com.renaser.os.support.domain.model.ticketsoporte.TicketSoporte;

import java.net.URI;

public record TicketSoporteVista(TicketSoporte ticket, URI attachmentUrl) {
}

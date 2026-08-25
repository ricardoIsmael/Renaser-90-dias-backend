package com.renaser.os.support.infrastructure.adapter.in.rest.ticketsoporte;

import jakarta.validation.constraints.Size;

public record ResolverTicketSoporteRequest(@Size(max = 4000) String adminNotes) {
}

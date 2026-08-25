package com.renaser.os.support.domain.model.ticketsoporte;

/** Estado de un ticket de soporte (tipo Postgres `estado_ticket_soporte`: 'ABIERTO','RESUELTO'). */
public enum EstadoTicketSoporte {

    ABIERTO,
    RESUELTO;

    public boolean estaResuelto() {
        return this == RESUELTO;
    }
}

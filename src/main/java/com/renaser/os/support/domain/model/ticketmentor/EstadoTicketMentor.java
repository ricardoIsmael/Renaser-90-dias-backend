package com.renaser.os.support.domain.model.ticketmentor;

public enum EstadoTicketMentor {

    ABIERTO,
    RESPONDIDO;

    public boolean estaAbierto() {
        return this == ABIERTO;
    }

    public boolean estaRespondido() {
        return this == RESPONDIDO;
    }
}

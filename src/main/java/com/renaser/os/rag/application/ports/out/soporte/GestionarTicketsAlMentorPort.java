package com.renaser.os.rag.application.ports.out.soporte;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Puerto propio de {@code rag} para los tickets del aprendiz a su mentor (2026-09-23, herramientas
 * {@code consultar_mis_tickets_al_mentor} y {@code proponer_ticket_al_mentor}). El adaptador delega
 * en {@code support.api.TicketsAlMentor}, que corre los mismos casos de uso que la app.
 *
 * <p>Propaga lo que propaga {@code support}: {@code NoSuchElementException} si la persona no existe,
 * {@code NotAuthorizedException} si esta suspendida o no es aprendiz.
 */
public interface GestionarTicketsAlMentorPort {

    /** El largo maximo de cada texto, el mismo que exige {@code support} al abrir. */
    int largoMaximoDeCadaTexto();

    /** Solo los que abrio esta persona, los mas nuevos primero. */
    List<TicketAlMentor> propios(UserId aprendizId);

    /** @return el instante en que quedo abierto */
    Instant abrir(UserId aprendizId, TextosDelTicket textos);

    record TextosDelTicket(String descripcionBloqueo, String solucionesIntentadas, String impactoMetaSmart) {
    }

    /** @param respuestaMentor {@code null} mientras el mentor no respondio */
    record TicketAlMentor(TextosDelTicket textos, boolean respondido, String respuestaMentor, Instant creadoEn,
                          Instant respondidoEn) {
    }
}

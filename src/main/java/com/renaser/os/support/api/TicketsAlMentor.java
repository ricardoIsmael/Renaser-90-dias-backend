package com.renaser.os.support.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contrato publico de {@code support} para que otro modulo lea y abra los tickets de un aprendiz a
 * su mentor (2026-09-23, herramientas {@code consultar_mis_tickets_al_mentor} y
 * {@code proponer_ticket_al_mentor} del acompanante, {@code rag}).
 *
 * <p><b>Delega, no reimplementa.</b> {@link #propios} corre {@code ListarTicketsMentorUseCase.propios}
 * y {@link #abrir} corre {@code AbrirTicketMentorUseCase.abrir}, los mismos de
 * {@code GET /api/v1/tickets} y {@code POST /api/v1/tickets}: el rol TRAINEE, la cuenta activa y
 * los largos se deciden ahi.
 *
 * <p>Propaga lo que propagan esos casos de uso: {@code NoSuchElementException} si la persona no
 * existe, {@code NotAuthorizedException} si esta suspendida o no es aprendiz.
 */
public interface TicketsAlMentor {

    /**
     * Largo maximo de cada uno de los tres textos. Es el que declara
     * {@code AbrirTicketMentorCommand} con {@code @Size}, que lo toma de aca: esta escrito una vez.
     */
    int LARGO_MAXIMO_TEXTO = 2000;

    /**
     * Los tickets que abrio ESTA persona, los mas nuevos primero (la primera pagina del caso de
     * uso). Nunca los de otro aprendiz, ni siquiera si quien pregunta es mentor.
     */
    List<TicketAlMentor> propios(UserId aprendizId);

    /** Mismo efecto que {@code POST /api/v1/tickets}. */
    TicketAbierto abrir(UserId aprendizId, TextosDelTicket textos);

    record TextosDelTicket(String descripcionBloqueo, String solucionesIntentadas, String impactoMetaSmart) {
    }

    /**
     * @param respondido      el estado del ticket ({@code RESPONDIDO}), no una deduccion
     * @param respuestaMentor {@code null} mientras el mentor no respondio
     * @param respondidoEn    {@code null} mientras el mentor no respondio
     */
    record TicketAlMentor(UUID id, TextosDelTicket textos, boolean respondido, String respuestaMentor,
                          Instant creadoEn, Instant respondidoEn) {
    }

    record TicketAbierto(UUID id, Instant creadoEn) {
    }
}

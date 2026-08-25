package com.renaser.os.support.infrastructure.adapter.out.persistence.ticketmentor;

/**
 * Espejo del tipo Postgres `estado_ticket_mentor`. Tipo local propio aunque los nombres
 * coincidan con EstadoTicketMentor de dominio — la entidad JPA no importa tipos de
 * dominio, siempre traduce via el mapper (mismo patron que NivelMentorJpa en `users`).
 */
public enum EstadoTicketMentorJpa {
    ABIERTO,
    RESPONDIDO
}

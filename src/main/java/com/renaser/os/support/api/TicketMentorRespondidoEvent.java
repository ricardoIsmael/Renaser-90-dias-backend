package com.renaser.os.support.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * El mentor respondio el ticket de un aprendiz. Lo escucha
 * {@code notifications.TicketMentorRespondidoNotificationListener} para avisarle al aprendiz (E-219).
 *
 * <p>{@code ticketId} es un {@link UUID}, igual que en {@link TicketMentorAbiertoEvent}: decia
 * {@code TicketMentorId}, un tipo de {@code support.domain} que ningun otro modulo puede ver
 * (corregido 2026-09-23).
 */
public record TicketMentorRespondidoEvent(UUID ticketId, UserId participanteId,
                                           Instant occurredAt) implements DomainEvent {
}

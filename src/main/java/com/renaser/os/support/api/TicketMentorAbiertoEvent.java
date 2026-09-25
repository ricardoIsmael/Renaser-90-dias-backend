package com.renaser.os.support.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Un aprendiz abrio un ticket a su mentor. Lo escucha
 * {@code notifications.TicketMentorAbiertoNotificationListener} para avisarle al mentor asignado (E-217).
 *
 * <p>{@code ticketId} es un {@link UUID} y no el {@code TicketMentorId} del dominio: este record lo
 * leen otros modulos, y un tipo de {@code support.domain} en su firma los obligaba a depender de un
 * paquete que Spring Modulith no les deja ver. El id del ticket es ademas la clave de deduplicacion
 * de la notificacion (C-7: el outbox reentrega at-least-once).
 */
public record TicketMentorAbiertoEvent(UUID ticketId, UserId participanteId,
                                        Instant occurredAt) implements DomainEvent {
}

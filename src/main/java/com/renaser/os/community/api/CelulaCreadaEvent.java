package com.renaser.os.community.api;

import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado cuando se crea una celula ({@code CelulaService.crear}). Primer consumidor:
 * {@code chat}, para crear automaticamente su conversacion de celula (idempotente, el
 * indice unico {@code conversaciones.celula_id} protege contra duplicados).
 */
public record CelulaCreadaEvent(UUID celulaId, Instant occurredAt) implements DomainEvent {
}

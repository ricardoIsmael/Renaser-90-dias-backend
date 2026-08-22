package com.renaser.os.shared.event;

import java.time.Instant;

/**
 * Marcador de eventos de dominio publicados entre modulos (CLAUDE.MD §4.4).
 * Spring Modulith los persiste en su tabla de outbox -> entrega at-least-once.
 */
public interface DomainEvent {

    Instant occurredAt();
}

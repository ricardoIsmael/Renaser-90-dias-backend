package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;

/** Spec 046, D7: Administración cambió o quitó el sobrenombre de una persona. Se le avisa sin el motivo. */
public record SobrenombreCambiadoPorAdministracionEvent(UserId persona, boolean quitado, Instant occurredAt) implements DomainEvent {
}

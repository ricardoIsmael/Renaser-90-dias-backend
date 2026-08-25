package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Publicado cuando una {@code SesionBloqueo} (Santuario) se rompe antes de tiempo. */
public record SantuarioRotoEvent(UUID registroId, UserId participanteId, Instant occurredAt)
        implements DomainEvent {
}

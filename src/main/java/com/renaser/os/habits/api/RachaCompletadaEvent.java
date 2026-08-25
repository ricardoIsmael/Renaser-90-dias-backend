package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Publicado cuando una {@code RachaSinCelular} completa el ciclo de 24h. */
public record RachaCompletadaEvent(UUID rachaId, UserId participanteId, Instant occurredAt)
        implements DomainEvent {
}

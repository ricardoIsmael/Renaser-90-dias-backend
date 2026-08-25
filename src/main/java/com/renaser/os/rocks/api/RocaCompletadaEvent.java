package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado cuando una Roca Diaria se completa. `points` se llama de forma
 * SINCRÓNICA (mismo `@Transactional`, ver CLAUDE.MD §9.1) — este evento es
 * para consumidores que sí pueden ser eventuales: `notifications` (Ola 3).
 *
 * <p>{@code rocaId} es un {@code UUID} plano, no {@code RocaDiariaId}: ese tipo vive en
 * {@code rocks.domain} (paquete interno, sin {@code @NamedInterface}) y exponerlo acá
 * filtraría bytecode hacia consumidores externos — mismo riesgo que documentó `support`
 * §3 sobre {@code users.api.UserSummary}. Mismo criterio que {@code HabitoCompletadoEvent}
 * de `habits`, que ya usa {@code UUID} para sus identificadores.
 */
public record RocaCompletadaEvent(UUID rocaId, UserId participanteId,
                                   Instant occurredAt) implements DomainEvent {
}

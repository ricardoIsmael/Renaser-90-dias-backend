package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado cuando un {@code RegistroHabito} pasa a COMPLETADO (incluye el ciclo
 * completo de 24h de Santuario/Racha sin celular). Escuchado por futuros
 * consumidores (`notifications`, Ola 3); hoy sin consumidores propios — se
 * publica igual porque `points` ya se actualiza SINCRÓNICAMENTE dentro de la
 * misma transacción (CLAUDE.MD §9.1), no vía este evento.
 */
public record HabitoCompletadoEvent(UUID registroId, UserId participanteId, UUID habitoId, int puntosOtorgados,
                                     Instant occurredAt) implements DomainEvent {
}

package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;

/**
 * Publicado cuando un usuario nuevo queda dado de alta — por aprobacion de
 * {@code AccountRequest} ({@code AccountRequestService.approve}) o por invitacion directa
 * ({@code UserAccountService.invite}). Primer consumidor: {@code chat}, para el auto-join
 * a la conversacion GLOBAL (V1__baseline_renaser.sql:1293-1295, "todo usuario nuevo se
 * agrega AUTOMATICAMENTE a la conversacion GLOBAL").
 */
public record UsuarioRegistradoEvent(UserId usuarioId, Instant occurredAt) implements DomainEvent {
}

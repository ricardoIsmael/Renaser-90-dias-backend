package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;

/**
 * Publicado cuando un administrador le cambia el rol a alguien
 * ({@code UserAccountService.updateRole}). Solo se publica si el rol REALMENTE cambio: reasignar
 * el mismo rol no es una novedad y no tiene por que despertar a ningun modulo.
 *
 * <p>Primer consumidor: {@code chat}, para el chat de soporte por aprendiz (D-136). Sin este
 * evento, alguien promovido a ADMIN/ALCHEMIST quedaba afuera de todas las conversaciones de
 * soporte ya existentes y solo veia las de los aprendices que entraran despues de su ascenso —
 * justo lo contrario de lo que se espera de un administrador nuevo.
 *
 * <p>Viaja el rol anterior ademas del nuevo porque quien reacciona casi siempre necesita saber en
 * que direccion se movio: entrar al staff y salir del staff no piden lo mismo.
 */
public record RolDeUsuarioCambiadoEvent(UserId usuarioId, UserRole rolAnterior, UserRole rolNuevo,
                                         Instant occurredAt) implements DomainEvent {
}

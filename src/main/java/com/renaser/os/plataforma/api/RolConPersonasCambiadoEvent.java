package com.renaser.os.plataforma.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Soporte TI cambió un rol personalizado que tiene personas asignadas (spec 002, US7 escenario 6; D-272): editó sus
 * funcionalidades, lo desactivó o lo reactivó, o lo reemplazó en bloque moviendo personas. Sale por el outbox en la
 * misma transacción que el cambio, y {@code notifications} avisa por push a todos los ADMIN y ALQUIMISTA activos
 * (decisión del dueño del 2026-10-14). Un cambio que no toca a nadie (sin personas, o solo el nombre o la descripción)
 * no lo publica.
 *
 * @param personas cuántas personas toca: las que tiene el rol, o las que se movieron en el reemplazo
 * @param resumen  qué cambió en palabras, por ejemplo «lo desactivó» o «las pasó a «Mentor inicial»»
 */
public record RolConPersonasCambiadoEvent(UUID rolId, String nombre, long personas, String resumen, UserId actor,
                                          String motivo, Instant occurredAt) implements DomainEvent {

    public RolConPersonasCambiadoEvent {
        Objects.requireNonNull(rolId, "rolId es obligatorio");
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }
}

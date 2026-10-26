package com.renaser.os.users.api;

import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cambió un rol personalizado o a quién está asignado (contrato de la fase 2, §4.1; D-272): se creó, se editó (también
 * desactivar o reactivar), se borró, se reemplazó en bloque, o Administración se lo asignó o se lo quitó a alguien.
 * {@code plataforma} lo escucha para recargar su copia en memoria (persona → rol, rol → funcionalidades) en el acto,
 * sin esperar la recarga de cada 30 s.
 *
 * @param rolId el rol que cambió; al quitar uno, el que se quitó
 */
public record RolPersonalizadoCambiadoEvent(UUID rolId, Instant occurredAt) implements DomainEvent {

    public RolPersonalizadoCambiadoEvent {
        Objects.requireNonNull(rolId, "rolId es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }
}

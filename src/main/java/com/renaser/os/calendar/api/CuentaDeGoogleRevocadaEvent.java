package com.renaser.os.calendar.api;

import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * La cuenta de Google que crea las salas de Meet dejó de servir (spec 040, FR-009): Google rechazó su permiso permanente. {@code notifications}
 * avisa a Administración y Alquimista que hay que volver a conectarla.
 *
 * <p>{@code avisoId} es el mismo durante todo un día (UTC), así que la deduplicación de la bandeja {@code (usuario, tipo, origenEventoId)} deja UN
 * aviso por día mientras la cuenta siga revocada, sin spam aunque falle mucho a la vez.
 */
public record CuentaDeGoogleRevocadaEvent(UUID avisoId, Instant occurredAt) implements DomainEvent {
}

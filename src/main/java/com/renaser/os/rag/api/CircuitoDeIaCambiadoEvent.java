package com.renaser.os.rag.api;

import com.renaser.os.shared.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * El circuito automático de una parte de SER se abrió o se cerró (spec 002, FR-016; contrato §7). Sale por el outbox en
 * la misma transacción que su fila {@code AUTOMATICO} de la auditoría. {@code notifications} avisa por push a Soporte
 * TI, como máximo 4 por hora por parte; es idempotente por {@code parte} + {@code occurredAt}.
 *
 * @param parte  {@code "ia.chat"} o {@code "ia.voz"}
 * @param estado {@code "ABIERTO"} o {@code "CERRADO"} (tomar la prueba no se avisa)
 * @param causa  en palabras, por ejemplo «5 fallas del proveedor en 2 min; se prueba de nuevo en 2 min»
 */
public record CircuitoDeIaCambiadoEvent(String parte, String estado, String causa, Instant occurredAt)
        implements DomainEvent {

    public CircuitoDeIaCambiadoEvent {
        Objects.requireNonNull(parte, "parte es obligatoria");
        Objects.requireNonNull(estado, "estado es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }
}

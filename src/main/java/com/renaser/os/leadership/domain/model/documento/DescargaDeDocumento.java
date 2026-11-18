package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cada vez que alguien pide un documento queda una fila (spec 056, FR-040): quién lo pidió, cuándo y si hubo que generarlo o ya
 * estaba (FR-039). Solo se agrega: no hay cómo cambiarla.
 *
 * @param reutilizado true si el documento ya existía con esos mismos datos y no se generó de nuevo
 */
public record DescargaDeDocumento(UUID id, UUID documentoId, UserId actorId, boolean reutilizado, Instant pedidoEn) {

    public DescargaDeDocumento {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(documentoId, "documentoId es obligatorio");
        Objects.requireNonNull(actorId, "actorId es obligatorio");
        Objects.requireNonNull(pedidoEn, "pedidoEn es obligatorio");
    }

    /** El id entra por {@code IdGenerator} y la hora por {@code Clock}: el dominio no genera ninguna. */
    public static DescargaDeDocumento registrar(UUID id, DocumentoSemanal documento, UserId actorId, boolean reutilizado, Clock clock) {
        return new DescargaDeDocumento(id, documento.id(), actorId, reutilizado, clock.now());
    }
}

package com.renaser.os.calendar.domain.model.confirmacion;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * RSVP de un usuario a UNA ocurrencia (tabla {@code confirmaciones_evento}, PK compuesta
 * {@code (evento_id, inicio_ocurrencia, usuario_id)} — sin id propio, P-28 del baseline).
 */
public record Confirmacion(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId, EstadoConfirmacion estado,
                            Instant creadoEn, Instant actualizadoEn) {

    public Confirmacion {
        Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        Objects.requireNonNull(inicioOcurrencia, "inicioOcurrencia es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(estado, "estado es obligatorio");
    }
}

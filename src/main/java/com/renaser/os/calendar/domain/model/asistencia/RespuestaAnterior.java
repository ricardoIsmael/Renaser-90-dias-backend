package com.renaser.os.calendar.domain.model.asistencia;

import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/** Una fila de {@code historial_confirmaciones_evento}: lo que una persona respondió y cuándo. */
public record RespuestaAnterior(UserId usuarioId, EstadoConfirmacion estado, Instant registradaEn) {

    public RespuestaAnterior {
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(estado, "estado es obligatorio");
        Objects.requireNonNull(registradaEn, "registradaEn es obligatorio");
    }
}

package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code PUT /attendance/{userId}} (D-256). {@code estado}: {@code A_TIEMPO}, {@code TARDE} o
 * {@code null} (= sin marcar, ausente).
 */
record MarcarAsistenciaRequest(@NotBlank String occurrenceStart, String estado) {
}

package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code tipoContenido}: el MIME exacto del archivo (p. ej. {@code image/jpeg},
 * {@code video/mp4}, {@code audio/m4a}). Tiene que ser el MISMO que el cliente manda como
 * {@code Content-Type} en el PUT a la URL prefirmada — si no coincide, S3 rechaza la firma.
 */
public record SolicitarUrlEvidenciaRegistroRequest(@NotBlank String tipoContenido) {
}

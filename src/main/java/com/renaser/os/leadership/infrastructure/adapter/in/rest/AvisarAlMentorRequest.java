package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/leadership/people/{personId}/avisar-al-mentor}.
 *
 * @param texto        lo que el líder le dice al mentor; el servidor le agrega el enlace a la ficha
 * @param operationKey la misma en un reintento: el aviso sale una sola vez
 */
public record AvisarAlMentorRequest(@NotBlank @Size(max = 1000) String texto, @NotBlank @Size(max = 100) String operationKey) {
}

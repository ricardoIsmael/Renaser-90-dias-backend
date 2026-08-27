package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espejo de {@code uploadGuideAttachment} (`habitsAdmin.ts`) para el paso "confirmar" del
 * patron upload-url -> PUT -> confirmar. {@code mediaType} solo admite IMAGE/AUDIO — LINK
 * sigue yendo por {@code CreateGuideAttachmentRequest}. */
public record ConfirmarAdjuntoGuiaArchivoRequest(int startDay, @NotNull HabitGuideSectionDto section,
                                                   @NotNull HabitGuideAttachmentMediaTypeDto mediaType,
                                                   @NotBlank String bucket, @NotBlank String ruta, String mimeType,
                                                   Integer sizeBytes, String originalName, String title) {
}

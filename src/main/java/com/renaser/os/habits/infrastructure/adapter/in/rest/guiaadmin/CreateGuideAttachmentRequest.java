package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espejo de {@code CreateGuideAttachmentInput} (`habitsAdmin.ts`) — solo ENLACE, ver
 * {@code CrearAdjuntoGuiaEnlaceUseCase} javadoc sobre por que IMAGEN/AUDIO no estan aca. */
public record CreateGuideAttachmentRequest(int startDay, @NotNull HabitGuideSectionDto section,
                                            @NotBlank String url, String title) {
}

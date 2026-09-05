package com.renaser.os.academy.infrastructure.adapter.in.rest.clasediaria;

import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Espejo de {@code CompletarClaseDiariaInput} (RenaserBack `clase-diaria/schema.ts`). */
public record CompletarClaseDiariaRequest(@NotBlank String leccionId,
                                           @NotBlank
                                           @Size(min = CompletarClaseDiariaUseCase.RESUMEN_MIN_LENGTH,
                                                   max = CompletarClaseDiariaUseCase.RESUMEN_MAX_LENGTH)
                                           String resumen) {
}

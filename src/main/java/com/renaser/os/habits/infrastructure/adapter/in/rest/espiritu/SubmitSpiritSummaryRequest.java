package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Sin campo `evidence`: este habito se entrega solo con resumen de texto (encargo explicito). */
public record SubmitSpiritSummaryRequest(@NotNull @Min(1) Integer day, @NotBlank String summaryText) {
}

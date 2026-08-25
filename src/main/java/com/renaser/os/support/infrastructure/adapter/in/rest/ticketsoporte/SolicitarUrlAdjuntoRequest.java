package com.renaser.os.support.infrastructure.adapter.in.rest.ticketsoporte;

import jakarta.validation.constraints.NotBlank;

public record SolicitarUrlAdjuntoRequest(@NotBlank String fileName, @NotBlank String contentType) {
}

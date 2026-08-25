package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ProgramarSesionRequest(@NotNull Instant scheduledAt) {
}

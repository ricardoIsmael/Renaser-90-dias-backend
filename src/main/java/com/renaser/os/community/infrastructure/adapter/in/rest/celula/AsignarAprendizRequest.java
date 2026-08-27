package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AsignarAprendizRequest(@NotNull UUID traineeId) {
}

package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

public record AnularVeredictoRequest(@NotBlank String notas) {
}

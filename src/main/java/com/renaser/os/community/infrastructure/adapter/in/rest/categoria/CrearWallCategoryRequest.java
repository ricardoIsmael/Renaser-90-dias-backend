package com.renaser.os.community.infrastructure.adapter.in.rest.categoria;

import jakarta.validation.constraints.NotBlank;

public record CrearWallCategoryRequest(@NotBlank String key, @NotBlank String label, @NotBlank String emoji) {
}

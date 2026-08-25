package com.renaser.os.community.infrastructure.adapter.in.rest.categoria;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReordenarWallCategoriesRequest(@NotEmpty List<String> keys) {
}

package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWallCommentRequest(@NotBlank @Size(max = 500) String text) {
}

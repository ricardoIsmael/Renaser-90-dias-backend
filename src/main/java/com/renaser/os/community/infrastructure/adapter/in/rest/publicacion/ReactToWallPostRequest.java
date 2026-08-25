package com.renaser.os.community.infrastructure.adapter.in.rest.publicacion;

import jakarta.validation.constraints.NotBlank;

/** {@code type} en ingles (LIKE/DISLIKE) — traducido en el controller. */
public record ReactToWallPostRequest(@NotBlank String type) {
}
